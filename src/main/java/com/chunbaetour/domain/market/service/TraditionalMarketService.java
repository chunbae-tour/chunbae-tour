package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyResponse;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
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

    private final TraditionalMarketRepository marketRepository;

    /**
     * 위치 기반 전통시장 조회 (커서 페이지네이션).
     *
     * @param lat 위도
     * @param lng 경도
     * @param radius 반경 (미터)
     * @param cursor 커서
     * @param size 페이지 크기
     * @return 근처 전통시장 목록
     */
    public CursorPageResponse<TraditionalMarketNearbyResponse> findNearby(
            BigDecimal lat, BigDecimal lng, int radius, String cursor, int size) {
        // TODO: SearchQueryRepository에 위치 기반 쿼리 추가 후 구현
        // 임시로 빈 응답 반환 (별도 구현 필요)
        return new CursorPageResponse<>(List.of(), null, false, 0);
    }
}
