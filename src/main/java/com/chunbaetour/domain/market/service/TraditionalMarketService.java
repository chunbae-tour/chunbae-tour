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
 * 위치 기반 조회: page 기반 offset pagination.
 * 관광지 nearby와 동일하게 distance ASC, id ASC 정렬.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TraditionalMarketService {

    private final TraditionalMarketQueryRepository queryRepository;

    /**
     * 위치 기반 전통시장 조회 (페이지 기반).
     * 관광지 nearby와 동일하게 distance ASC, id ASC 정렬 + offset pagination.
     */
    public TraditionalMarketNearbyPageResponse findNearby(
            BigDecimal lat, BigDecimal lng, int radius, int page, int size) {

        // size + 1 조회 (hasNext 판정)
        List<TraditionalMarketNearbyResponse> results = queryRepository.findNearby(
                lat.doubleValue(), lng.doubleValue(), radius,
                (long) page * size, size + 1
        );

        boolean hasNext = results.size() > size;
        if (hasNext) {
            results = results.subList(0, size);
        }

        return new TraditionalMarketNearbyPageResponse(results, page, size, hasNext);
    }
}
