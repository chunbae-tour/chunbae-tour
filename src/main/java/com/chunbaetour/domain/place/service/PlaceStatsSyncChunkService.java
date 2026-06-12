package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceStatsSyncChunkService {

    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> ATOMIC_CLEANUP_SCRIPT = 
        new org.springframework.data.redis.core.script.DefaultRedisScript<>(
            "local viewKey = KEYS[1];\n" +
            "local likeKey = KEYS[2];\n" +
            "local dirtyKey = KEYS[3];\n" +
            "local expectedView = ARGV[1];\n" +
            "local expectedLike = ARGV[2];\n" +
            "local placeId = ARGV[3];\n" +
            "local actualView = redis.call('get', viewKey);\n" +
            "local viewMatch = false;\n" +
            "if expectedView == '' then viewMatch = (not actualView) else viewMatch = (actualView == expectedView) end;\n" +
            "local actualLike = redis.call('get', likeKey);\n" +
            "local likeMatch = false;\n" +
            "if expectedLike == '' then likeMatch = (not actualLike) else likeMatch = (actualLike == expectedLike) end;\n" +
            "if viewMatch and likeMatch then\n" +
            "  redis.call('srem', dirtyKey, placeId);\n" +
            "  return 1;\n" +
            "else\n" +
            "  return 0;\n" +
            "end;", 
            Long.class
        );

    public void syncChunk(List<String> dirtyIds) {
        List<String> viewKeys = new ArrayList<>();
        List<String> likeKeys = new ArrayList<>();

        for (String id : dirtyIds) {
            viewKeys.add(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + id);
            likeKeys.add(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + id);
        }

        List<String> viewCounts = stringRedisTemplate.opsForValue().multiGet(viewKeys);
        List<String> likeCounts = stringRedisTemplate.opsForValue().multiGet(likeKeys);

        if (viewCounts == null || likeCounts == null) {
            log.warn("Redis multiGet returned null for keys. Aborting chunk.");
            return;
        }

        List<StatsUpdateDto> updates = new ArrayList<>();
        List<Long> emptyIds = new ArrayList<>();

        for (int i = 0; i < dirtyIds.size(); i++) {
            String idStr = dirtyIds.get(i);
            String vcStr = viewCounts.get(i);
            String lcStr = likeCounts.get(i);

            try {
                Long id = Long.parseLong(idStr);
                if (vcStr == null && lcStr == null) {
                    emptyIds.add(id);
                } else {
                    Integer viewCount = vcStr != null ? Integer.valueOf(vcStr) : null;
                    Integer likeCount = lcStr != null ? Integer.valueOf(lcStr) : null;
                    updates.add(new StatsUpdateDto(id, viewCount, likeCount));
                }
            } catch (Exception e) {
                log.error("카운터 파싱 실패 - 무한 재시도 방지를 위해 더티 큐에서 즉시 제거 (placeId: {})", idStr, e);
                stringRedisTemplate.opsForSet().remove(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, idStr);
            }
        }

        int updatedRows = 0;
        if (!updates.isEmpty()) {
            updates.sort(java.util.Comparator.comparing(StatsUpdateDto::id));

            String sql = "UPDATE places SET " +
                         "view_count = GREATEST(view_count, COALESCE(?, view_count)), " +
                         "like_count = COALESCE(?, like_count) " +
                         "WHERE id = ?";

            Integer updatedRowsCount = transactionTemplate.execute(status -> {
                int[] results = jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
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
            updatedRows = updatedRowsCount != null ? updatedRowsCount : 0;
        }

        for (StatsUpdateDto dto : updates) {
            String expectedViewStr = dto.viewCount() != null ? String.valueOf(dto.viewCount()) : "";
            String expectedLikeStr = dto.likeCount() != null ? String.valueOf(dto.likeCount()) : "";
            executeLuaCleanup(dto.id(), expectedViewStr, expectedLikeStr);
        }

        for (Long id : emptyIds) {
            executeLuaCleanup(id, "", "");
        }

        log.info("Place stats sync chunk completed: {} requested, {} rows updated, {} empty cleaned.", dirtyIds.size(), updatedRows, emptyIds.size());
    }

    private void executeLuaCleanup(Long id, String expectedViewStr, String expectedLikeStr) {
        stringRedisTemplate.execute(
            ATOMIC_CLEANUP_SCRIPT,
            java.util.Arrays.asList(
                PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + id,
                PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + id,
                PlaceRedisConstants.PLACE_DIRTY_STATS_KEY
            ),
            expectedViewStr,
            expectedLikeStr,
            String.valueOf(id)
        );
    }

    private record StatsUpdateDto(Long id, Integer viewCount, Integer likeCount) {}
}
