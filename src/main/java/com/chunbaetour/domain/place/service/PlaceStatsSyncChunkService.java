package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Redis에 누적된 관광지 조회수/좋아요 카운터를 청크 단위로 DB에 반영한다.
 *
 * <p>dirty marker는 통계 유실을 막기 위한 유일한 재발견 인덱스이므로, ID 자체가 잘못된
 * poison entry일 때만 제거한다. 카운터 값 손상이나 Redis eviction 의심 상황은 다음 배치에서
 * 재처리할 수 있도록 dirty marker를 보존한다.
 */
@Slf4j
@Service
public class PlaceStatsSyncChunkService {

    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public PlaceStatsSyncChunkService(
            StringRedisTemplate stringRedisTemplate,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        // Admin audit 전용 TransactionTemplate 빈에 암묵 의존하지 않도록 서비스 전용 템플릿을 구성한다.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void syncChunk(List<String> dirtyIds) {
        List<StatsUpdateDto> updates = new ArrayList<>();
        int emptyCounterCount = 0;

        for (String idStr : dirtyIds) {
            Long id = parseDirtyIdOrRemove(idStr);
            if (id == null) {
                continue;
            }

            String vcStr = getCounterValue(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX, id);
            String lcStr = getCounterValue(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX, id);

            if (vcStr == null && lcStr == null) {
                emptyCounterCount++;
                log.warn("Place stats counters are missing. Keep dirty marker for retry. placeId={}", id);
                continue;
            }

            try {
                Integer viewCount = parseNullableCounter(vcStr);
                Integer likeCount = parseNullableCounter(lcStr);
                updates.add(new StatsUpdateDto(id, viewCount, likeCount));
            } catch (NumberFormatException e) {
                log.warn(
                        "Place stats counter parsing failed. Keep dirty marker for retry. placeId={}, viewCount={}, likeCount={}",
                        id, vcStr, lcStr, e
                );
            }
        }

        int updatedRows = updatePlaceStats(updates);
        cleanupSyncedDirtyMarkers(updates);

        log.info(
                "Place stats sync chunk completed: {} requested, {} rows updated, {} empty counters kept.",
                dirtyIds.size(), updatedRows, emptyCounterCount
        );
    }

    private Long parseDirtyIdOrRemove(String idStr) {
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            log.error("Invalid dirty place ID. Removing poison entry from dirty set. placeId={}", idStr, e);
            stringRedisTemplate.opsForSet().remove(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, idStr);
            return null;
        }
    }

    /**
     * Reads a single Redis counter key.
     *
     * <p>Redis Cluster cannot MGET arbitrary place counters because different place IDs map to
     * different slots. Single-key GET keeps this path cluster-safe and returns {@code null} for
     * missing keys.
     */
    private String getCounterValue(String prefix, Long id) {
        return stringRedisTemplate.opsForValue().get(prefix + id);
    }

    private Integer parseNullableCounter(String value) {
        return value != null ? Integer.valueOf(value) : null;
    }

    private int updatePlaceStats(List<StatsUpdateDto> updates) {
        if (updates.isEmpty()) {
            return 0;
        }

        updates.sort(java.util.Comparator.comparing(StatsUpdateDto::id));

        String sql = "UPDATE places SET " +
                "view_count = GREATEST(view_count, COALESCE(?, view_count)), " +
                "like_count = COALESCE(?, like_count) " +
                "WHERE id = ?";

        Integer updatedRowsCount = transactionTemplate.execute(status -> {
            int[] results = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    StatsUpdateDto dto = updates.get(i);
                    if (dto.viewCount() != null) ps.setInt(1, dto.viewCount());
                    else ps.setNull(1, java.sql.Types.INTEGER);

                    if (dto.likeCount() != null) ps.setInt(2, dto.likeCount());
                    else ps.setNull(2, java.sql.Types.INTEGER);

                    ps.setLong(3, dto.id());
                }

                @Override
                public int getBatchSize() {
                    return updates.size();
                }
            });

            int count = 0;
            for (int r : results) {
                if (r > 0) count++;
            }
            return count;
        });

        return updatedRowsCount != null ? updatedRowsCount : 0;
    }

    private void cleanupSyncedDirtyMarkers(List<StatsUpdateDto> updates) {
        for (StatsUpdateDto dto : updates) {
            String expectedViewStr = dto.viewCount() != null ? String.valueOf(dto.viewCount()) : "";
            String expectedLikeStr = dto.likeCount() != null ? String.valueOf(dto.likeCount()) : "";
            cleanupDirtyMarkerIfUnchanged(dto.id(), expectedViewStr, expectedLikeStr);
        }
    }

    private void cleanupDirtyMarkerIfUnchanged(Long id, String expectedViewStr, String expectedLikeStr) {
        String viewKey = PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + id;
        String likeKey = PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + id;
        String placeId = String.valueOf(id);

        if (!matchesExpected(stringRedisTemplate.opsForValue().get(viewKey), expectedViewStr)
                || !matchesExpected(stringRedisTemplate.opsForValue().get(likeKey), expectedLikeStr)) {
            return;
        }

        try {
            stringRedisTemplate.opsForSet().remove(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, placeId);

            // Redis Cluster에서는 counter key와 dirty set을 한 Lua/transaction으로 묶을 수 없다.
            // SREM 직후 값을 다시 확인해, 동시 증가가 끼어든 경우 dirty marker를 복구한다.
            if (!matchesExpected(stringRedisTemplate.opsForValue().get(viewKey), expectedViewStr)
                    || !matchesExpected(stringRedisTemplate.opsForValue().get(likeKey), expectedLikeStr)) {
                restoreDirtyMarker(placeId);
            }
        } catch (RuntimeException e) {
            try {
                restoreDirtyMarker(placeId);
            } catch (RuntimeException restoreException) {
                e.addSuppressed(restoreException);
                log.error("Place stats dirty cleanup failed and marker restore also failed. placeId={}", placeId, e);
                throw e;
            }
            log.error("Place stats dirty cleanup failed after sync. Restored marker for retry. placeId={}", placeId, e);
            throw e;
        }
    }

    private void restoreDirtyMarker(String placeId) {
        stringRedisTemplate.opsForSet().add(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, placeId);
    }

    /**
     * Compares a Redis counter with the value that was just written to DB.
     *
     * <p>An empty expected value means the counter was absent during sync, so only a {@code null}
     * Redis value still matches. This keeps later-created counters dirty for the next batch.
     */
    private boolean matchesExpected(String actual, String expected) {
        return expected.isEmpty() ? actual == null : expected.equals(actual);
    }

    private record StatsUpdateDto(Long id, Integer viewCount, Integer likeCount) {}
}
