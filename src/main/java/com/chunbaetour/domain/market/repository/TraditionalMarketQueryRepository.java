package com.chunbaetour.domain.market.repository;

import com.chunbaetour.domain.market.entity.QTraditionalMarket;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyResponse;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberTemplate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.chunbaetour.domain.market.entity.QTraditionalMarket.traditionalMarket;

/**
 * 전통시장 공간 쿼리 레포지토리.
 * ST_Distance_Sphere를 이용한 위치 기반 검색.
 */
@Repository
@RequiredArgsConstructor
public class TraditionalMarketQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 지정 좌표 기반 반경 내 전통시장 조회 (page 기반 offset pagination).
     * 1단계: 바운딩박스 필터 (lat/lng BETWEEN, idx_traditional_markets_lat_lng 활용)
     * 2단계: ST_Distance_Sphere 정밀 거리 계산 + LIMIT/OFFSET
     *
     * @param lat 위도
     * @param lng 경도
     * @param radiusMeters 반경 (미터)
     * @param offset OFFSET (page * size)
     * @param limit LIMIT (size + 1, hasNext 판정용)
     * @return 거리 오름차순 시장 목록
     */
    public List<TraditionalMarketNearbyResponse> findNearby(
            double lat, double lng, double radiusMeters, long offset, int limit) {

        double latDelta = (radiusMeters / 1000.0) / 111.0;
        // cos(±90°) ≈ 0 방어 — Infinity 방지
        double cosLat = Math.max(0.00001, Math.abs(Math.cos(Math.toRadians(lat))));
        double lngDelta = latDelta / cosLat;

        NumberTemplate<Double> distance = Expressions.numberTemplate(Double.class,
                "ST_Distance_Sphere(POINT({0}, {1}), POINT({2}, {3}))",
                traditionalMarket.lng, traditionalMarket.lat, lng, lat);

        List<Tuple> tuples = queryFactory
                .select(traditionalMarket, distance)
                .from(traditionalMarket)
                .where(
                        traditionalMarket.lat.between(lat - latDelta, lat + latDelta),
                        traditionalMarket.lng.between(lng - lngDelta, lng + lngDelta),
                        distance.loe(radiusMeters)
                )
                .orderBy(distance.asc(), traditionalMarket.id.asc())
                .offset(offset)
                .limit(limit)
                .fetch();

        return tuples.stream()
                .map(tuple -> {
                    TraditionalMarket market = tuple.get(0, TraditionalMarket.class);
                    Double dist = tuple.get(1, Double.class);
                    return TraditionalMarketNearbyResponse.builder()
                            .id(market.getId())
                            .name(market.getName())
                            .address(market.getAddress())
                            .lat(market.getLat())
                            .lng(market.getLng())
                            .marketType(market.getMarketType())
                            .distanceMeters(dist)
                            .imageUrl(null)
                            .targetType("TRADITIONAL_MARKET")
                            .build();
                })
                .toList();
    }
}
