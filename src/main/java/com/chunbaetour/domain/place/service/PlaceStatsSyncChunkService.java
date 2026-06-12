package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 조회수/좋아요 통계 비동기 배치의 청크 단위를 처리하는 서비스 (KAN-279).
 * 트랜잭션 격리를 위해 분리됨.
 */
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
            "local viewMatch = (expectedView == '' or redis.call('get', viewKey) == expectedView);\n" +
            "local likeMatch = (expectedLike == '' or redis.call('get', likeKey) == expectedLike);\n" +
            "if viewMatch and likeMatch then\n" +
            "  if expectedView ~= '' then redis.call('del', viewKey) end;\n" +
            "  if expectedLike ~= '' then redis.call('del', likeKey) end;\n" +
            "  redis.call('srem', dirtyKey, placeId);\n" +
            "  return 1;\n" +
            "else\n" +
            "  return 0;\n" +
            "end;", 
            Long.class
        );

    public void syncChunk(List<String> dirtyIds) {
        // 1. Redis에서 해당 ID들의 최신 통계 조회
        List<String> viewKeys = new ArrayList<>();
        List<String> likeKeys = new ArrayList<>();
        for (String id : dirtyIds) {
            viewKeys.add(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + id);
            likeKeys.add(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + id);
        }

        List<String> viewCounts = stringRedisTemplate.opsForValue().multiGet(viewKeys);
        List<String> likeCounts = stringRedisTemplate.opsForValue().multiGet(likeKeys);

        List<StatsUpdateDto> updates = new ArrayList<>();
        for (int i = 0; i < dirtyIds.size(); i++) {
            String idStr = dirtyIds.get(i);
            String vcStr = viewCounts != null ? viewCounts.get(i) : null;
            String lcStr = likeCounts != null ? likeCounts.get(i) : null;

            if (vcStr == null && lcStr == null) {
                continue;
            }

            try {
                Long id = Long.parseLong(idStr);
                Integer viewCount = vcStr != null ? Integer.valueOf(vcStr) : null;
                Integer likeCount = lcStr != null ? Integer.valueOf(lcStr) : null;

                updates.add(new StatsUpdateDto(id, viewCount, likeCount));
            } catch (Exception e) {
                log.error("[PlaceStatsSync] 카운터 파싱 실패 - 항목 건너뜀: id={}, vc={}, lc={}", idStr, vcStr, lcStr, e);
            }
        }

        if (updates.isEmpty()) {
            return;
        }

        // 2. JdbcTemplate을 활용한 Bulk Update
        String sql = "UPDATE places SET " +
                     "view_count = COALESCE(?, view_count), " +
                     "like_count = COALESCE(?, like_count) " +
                     "WHERE id = ?";

        Integer updatedRowsCount = transactionTemplate.execute(status -> {
            int[] results = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    StatsUpdateDto dto = updates.get(i);
                    
                    if (dto.viewCount() != null) {
                        ps.setInt(1, dto.viewCount());
                    } else {
                        ps.setNull(1, java.sql.Types.INTEGER);
                    }
                    
                    if (dto.likeCount() != null) {
                        ps.setInt(2, dto.likeCount());
                    } else {
                        ps.setNull(2, java.sql.Types.INTEGER);
                    }
                    
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

        int updatedRows = updatedRowsCount != null ? updatedRowsCount : 0;

        // 3. Race Condition 방지를 위한 카운터 삭제 및 Dirty 마커(SREM) 동시 원자적 제거
        // 찰나의 타이밍에 유저 트래픽으로 Redis 값이 변했다면 삭제와 SREM을 모두 보류하고 다음 배치에서 재처리
        for (StatsUpdateDto dto : updates) {
            String expectedViewStr = dto.viewCount() != null ? String.valueOf(dto.viewCount()) : "";
            String expectedLikeStr = dto.likeCount() != null ? String.valueOf(dto.likeCount()) : "";
            
            stringRedisTemplate.execute(
                ATOMIC_CLEANUP_SCRIPT,
                java.util.Arrays.asList(
                    PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + dto.id(),
                    PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + dto.id(),
                    PlaceRedisConstants.PLACE_DIRTY_STATS_KEY
                ),
                expectedViewStr,
                expectedLikeStr,
                String.valueOf(dto.id())
            );
        }

        log.info("Place stats sync chunk completed: {} requested, {} rows updated.", updates.size(), updatedRows);
    }

    private record StatsUpdateDto(Long id, Integer viewCount, Integer likeCount) {}
}
