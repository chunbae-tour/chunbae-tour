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

        // 3. 동기화가 성공한 카운터 키들을 삭제할 때 Race Condition 방지
        // 찰나의 타이밍에 유저 트래픽으로 Redis 값이 변했을 수 있으므로, 배치가 읽었던 값과 정확히 일치할 때만 삭제(Atomic Delete)
        String atomicDeleteScript = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        org.springframework.data.redis.core.script.DefaultRedisScript<Long> script = new org.springframework.data.redis.core.script.DefaultRedisScript<>(atomicDeleteScript, Long.class);

        for (StatsUpdateDto dto : updates) {
            if (dto.viewCount() != null) {
                stringRedisTemplate.execute(script, java.util.Collections.singletonList(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + dto.id()), String.valueOf(dto.viewCount()));
            }
            if (dto.likeCount() != null) {
                stringRedisTemplate.execute(script, java.util.Collections.singletonList(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + dto.id()), String.valueOf(dto.likeCount()));
            }
        }

        log.info("Place stats sync chunk completed: {} requested, {} rows updated.", updates.size(), updatedRows);
    }

    private record StatsUpdateDto(Long id, Integer viewCount, Integer likeCount) {}
}
