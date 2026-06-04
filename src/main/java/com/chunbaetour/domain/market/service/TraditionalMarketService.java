package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyResponse;
import com.chunbaetour.domain.market.repository.TraditionalMarketQueryRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전통시장 조회 서비스.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TraditionalMarketService {

    private final TraditionalMarketQueryRepository queryRepository;

    /**
     * 위치 기반 전통시장 조회 (커서 페이지네이션).
     * ST_Distance_Sphere를 이용한 공간 쿼리로 거리순 정렬.
     * 커서: "cursorId.cursorDistance" 형식 (URL-safe base64, 파싱은 Service 레이어)
     *
     * @param lat 위도
     * @param lng 경도
     * @param radius 반경 (미터)
     * @param cursor 커서 (다음 페이지 시작점: "id.distance")
     * @param size 페이지 크기 (1-100)
     * @return 거리 오름차순 커서 페이지 응답
     */
    public CursorPageResponse<TraditionalMarketNearbyResponse> findNearby(
            BigDecimal lat, BigDecimal lng, int radius, String cursor, int size) {

        // 커서 파싱: "123|45.67" → id=123, distance=45.67
        Long cursorId = null;
        Double cursorDistance = null;

        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = cursor.split("\\|");
                if (parts.length >= 2) {
                    cursorId = Long.parseLong(parts[0]);
                    cursorDistance = Double.parseDouble(parts[1]);
                }
            } catch (NumberFormatException e) {
                log.warn("[TraditionalMarket] 커서 파싱 실패: {}", cursor);
                // 파싱 실패 시 커서 무시 (첫 페이지부터 시작)
                cursorId = null;
                cursorDistance = null;
            }
        }

        // 커서 조건으로 size + 1 조회 (다음 페이지 존재 판단용)
        List<TraditionalMarketNearbyResponse> results = queryRepository.findNearby(
                lat.doubleValue(), lng.doubleValue(), radius,
                cursorId, cursorDistance, size + 1
        );

        // hasNext 판정: 조회 결과가 size + 1개 이상인지 확인
        boolean hasNext = results.size() > size;
        if (hasNext) {
            results = results.subList(0, size);
        }

        // 다음 커서 생성: 마지막 항목의 id|distanceMeters
        String nextCursor = null;
        if (!results.isEmpty() && hasNext) {
            TraditionalMarketNearbyResponse lastItem = results.get(results.size() - 1);
            nextCursor = String.format("%d|%.2f", lastItem.id(), lastItem.distanceMeters());
        }

        return new CursorPageResponse<>(results, nextCursor, hasNext, results.size());
    }
}
