package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyResponse;
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

    /**
     * 위치 기반 전통시장 조회 (커서 페이지네이션).
     * TODO: TraditionalMarketQueryRepository 구현 필요 (Place 패턴 준용)
     *
     * @param lat 위도
     * @param lng 경도
     * @param radius 반경 (미터)
     * @param cursor 커서 (다음 페이지ID.거리 형식)
     * @param size 페이지 크기
     * @return 근처 전통시장 목록 (거리 오름차순)
     */
    public CursorPageResponse<TraditionalMarketNearbyResponse> findNearby(
            BigDecimal lat, BigDecimal lng, int radius, String cursor, int size) {
        // TODO: QueryDSL로 ST_Distance_Sphere 쿼리 구현
        return new CursorPageResponse<>(List.of(), null, false, 0);
    }
}
