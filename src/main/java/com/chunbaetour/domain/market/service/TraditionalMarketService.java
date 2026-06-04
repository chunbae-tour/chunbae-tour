package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyPageResponse;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyResponse;
import com.chunbaetour.domain.market.repository.TraditionalMarketQueryRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전통시장 조회 서비스.
 * 위치 기반 조회: keyset pagination (cursor=id, cursorDistance=distance).
 * 관광지 nearby(NearbyPlaceRequest)와 동일한 cursor 방식으로 통일.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TraditionalMarketService {

    private final TraditionalMarketQueryRepository queryRepository;

    /**
     * 위치 기반 전통시장 조회 (커서 페이지네이션).
     * cursor(id) + cursorDistance 쌍으로 keyset pagination.
     * 관광지 nearby와 동일한 방식 — 프론트는 마지막 item의 id·distanceMeters를 다음 cursor로 사용.
     */
    public TraditionalMarketNearbyPageResponse findNearby(
            BigDecimal lat, BigDecimal lng, int radius,
            Long cursor, Double cursorDistance, int size) {

        // size + 1 조회 (hasNext 판정)
        List<TraditionalMarketNearbyResponse> results = queryRepository.findNearby(
                lat.doubleValue(), lng.doubleValue(), radius,
                cursor, cursorDistance, size + 1
        );

        boolean hasNext = results.size() > size;
        if (hasNext) {
            results = results.subList(0, size);
        }

        return new TraditionalMarketNearbyPageResponse(results, hasNext);
    }
}
