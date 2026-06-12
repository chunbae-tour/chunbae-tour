package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.place.constant.PlaceRedisConstants;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 조회수 및 좋아요 통계 비동기 배치 동기화 서비스 (KAN-279).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceStatsSyncBatchService {

    private final StringRedisTemplate stringRedisTemplate;
    private final PlaceStatsSyncChunkService placeStatsSyncChunkService;

    private static final int CHUNK_SIZE = 100;

    /**
     * 더티 마킹된 관광지의 조회수/좋아요 수를 Redis에서 읽어 DB로 일괄 갱신한다.
     * 전체 루프를 단일 트랜잭션으로 묶지 않고 청크마다 분리하여 DB 커넥션 점유를 최소화한다.
     */
    public void syncDirtyStats() {
        while (true) {
            // 1. 더티 마킹된 ID 청크(최대 CHUNK_SIZE개) 꺼내기 (pop 대신 randomMembers로 조회만 수행)
            List<String> dirtyIds = stringRedisTemplate.opsForSet().randomMembers(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, CHUNK_SIZE);
            if (dirtyIds == null || dirtyIds.isEmpty()) {
                break; // 더 이상 동기화할 데이터가 없음
            }

            try {
                placeStatsSyncChunkService.syncChunk(dirtyIds);
                // DB 갱신까지 완벽히 성공하면 SRANDMEMBER로 가져온 ID들을 더티 큐에서 SREM 삭제
                stringRedisTemplate.opsForSet().remove(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, dirtyIds.toArray(new Object[0]));
            } catch (Exception e) {
                log.error("[PlaceStatsSync] 청크 동기화 실패. 삭제(SREM)를 보류하여 데이터 유실을 방지합니다. 실패 ID 수: {}", dirtyIds.size(), e);
                throw e;
            }
        }
    }
}
