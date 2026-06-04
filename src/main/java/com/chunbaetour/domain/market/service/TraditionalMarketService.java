package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyResponse;
import com.chunbaetour.domain.market.repository.TraditionalMarketQueryRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전통시장 조회 서비스.
 * 위치 기반 조회: Base64 커서 (id|distance 전정밀도) + keyset pagination.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TraditionalMarketService {

    private final TraditionalMarketQueryRepository queryRepository;

    /**
     * 위치 기반 전통시장 조회 (커서 페이지네이션).
     * ST_Distance_Sphere + keyset pagination (dist > c OR (dist=c AND id>cid)).
     * 커서: "id|distance" Base64URL 인코딩 (CursorUtils 패턴).
     *
     * @param lat 위도
     * @param lng 경도
     * @param radius 반경 (미터)
     * @param cursor 커서 (Base64: "id|distance")
     * @param size 페이지 크기
     * @return 거리순 커서 페이지 응답
     */
    public CursorPageResponse<TraditionalMarketNearbyResponse> findNearby(
            BigDecimal lat, BigDecimal lng, int radius, String cursor, int size) {

        Long cursorId = null;
        Double cursorDistance = null;

        if (cursor != null && !cursor.isBlank()) {
            try {
                // Base64 디코딩: "id|distance" 복원
                String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = decoded.split("\\|");
                if (parts.length == 2) {
                    cursorId = Long.parseLong(parts[0]);
                    cursorDistance = Double.parseDouble(parts[1]);
                }
            } catch (Exception e) {
                log.warn("[TraditionalMarket] 커서 파싱 실패: {}", cursor);
            }
        }

        // size + 1 조회 (hasNext 판정)
        List<TraditionalMarketNearbyResponse> results = queryRepository.findNearby(
                lat.doubleValue(), lng.doubleValue(), radius,
                cursorId, cursorDistance, size + 1
        );

        boolean hasNext = results.size() > size;
        if (hasNext) {
            results = results.subList(0, size);
        }

        // 다음 커서: "id|distance(전정밀도)" → Base64 인코딩
        // 경계행 중복 방지: 거리를 반올림하지 않고 전정밀도로 인코딩
        String nextCursor = null;
        if (!results.isEmpty() && hasNext) {
            TraditionalMarketNearbyResponse lastItem = results.get(results.size() - 1);
            String raw = String.format("%d|%s", lastItem.id(), Double.toString(lastItem.distanceMeters()));
            nextCursor = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        return new CursorPageResponse<>(results, nextCursor, hasNext, results.size());
    }
}
