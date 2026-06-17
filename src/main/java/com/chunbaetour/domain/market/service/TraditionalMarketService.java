package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.like.service.UserLikeService;
import com.chunbaetour.domain.like.type.LikeTargetType;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketDetailResponse;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyPageResponse;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyResponse;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketQueryRepository;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
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

    private final TraditionalMarketRepository marketRepository;
    private final TraditionalMarketQueryRepository queryRepository;
    private final UserLikeService userLikeService;

    /**
     * 전통시장 단건 상세 조회.
     *
     * <p>공공데이터 기반 시장은 별도 ACTIVE 상태가 없으므로 존재 여부만 검증합니다. 로그인 사용자는 찜 여부를 함께
     * 내려주고, 비로그인 사용자는 {@code isLiked=false}로 고정합니다.
     */
    public TraditionalMarketDetailResponse getDetail(Long marketId, Long userId) {
        TraditionalMarket market = marketRepository.findById(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
        boolean isLiked = userId != null && userLikeService.isLiked(userId, LikeTargetType.MARKET, marketId);
        return TraditionalMarketDetailResponse.of(market, isLiked);
    }

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
