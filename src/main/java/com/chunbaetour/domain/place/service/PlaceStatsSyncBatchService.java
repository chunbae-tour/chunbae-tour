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
        // Cursor 기반으로 안전하게 순회하여 무한 루프 방지
        try (org.springframework.data.redis.core.Cursor<String> cursor = stringRedisTemplate.opsForSet().scan(
                PlaceRedisConstants.PLACE_DIRTY_STATS_KEY,
                org.springframework.data.redis.core.ScanOptions.scanOptions().count(CHUNK_SIZE).build())) {
            
            java.util.List<String> chunk = new java.util.ArrayList<>();
            while (cursor.hasNext()) {
                chunk.add(cursor.next());
                if (chunk.size() >= CHUNK_SIZE) {
                    tryProcessChunk(chunk);
                    chunk.clear();
                }
            }
            if (!chunk.isEmpty()) {
                tryProcessChunk(chunk);
            }
        } catch (Exception e) {
            log.error("[PlaceStatsSync] 커서 스캔 및 동기화 중 오류 발생", e);
        }
    }

    /**
     * 청크 처리 실패 시 해당 청크만 건너뛰고 나머지 순회를 계속하도록 예외를 격리한다.
     * 1분 뒤 스케줄러가 재실행되므로 실패 청크의 ID들은 더티 큐에 그대로 남아 자동 재처리된다.
     */
    private void tryProcessChunk(List<String> dirtyIds) {
        try {
            placeStatsSyncChunkService.syncChunk(dirtyIds);
            // DB 갱신까지 완벽히 성공하면 SREM 삭제
            stringRedisTemplate.opsForSet().remove(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, (Object[]) dirtyIds.toArray(new String[0]));
        } catch (Exception e) {
            log.error("[PlaceStatsSync] 청크 동기화 실패. 해당 청크를 건너뛰고 다음 청크를 계속 처리합니다. 실패 ID 수: {}", dirtyIds.size(), e);
            // throw 하지 않으므로 while 루프가 계속 진행됨
        }
    }
}
