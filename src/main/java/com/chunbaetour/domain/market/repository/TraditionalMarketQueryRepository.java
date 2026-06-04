package com.chunbaetour.domain.market.repository;

import com.chunbaetour.domain.market.entity.QTraditionalMarket;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyResponse;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
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
     * 지정 좌표 기반 반경 내 전통시장 조회.
     * 1단계: 바운딩박스 필터 (lat/lng BETWEEN, idx_traditional_markets_lat_lng 활용)
     * 2단계: ST_Distance_Sphere 정밀 거리 계산 (범위 필터 후)
     * keyset pagination: dist > c OR (dist = c AND id > cid)
     *
     * @param lat 위도
     * @param lng 경도
     * @param radiusMeters 반경 (미터)
     * @param cursorId 커서 ID
     * @param cursorDistance 커서 거리
     * @param size 페이지 크기
     * @return 거리 오름차순 시장 목록
     */
    public List<TraditionalMarketNearbyResponse> findNearby(
            double lat, double lng, double radiusMeters,
            Long cursorId, Double cursorDistance, int size) {

        // 대략적인 경위도 변화: 1도 ≈ 111km
        // 반경 radiusMeters에서 대략적인 bbox 계산
        double latDelta = (radiusMeters / 1000.0) / 111.0;
        double lngDelta = latDelta / Math.cos(Math.toRadians(lat));

        NumberTemplate<Double> distance = Expressions.numberTemplate(Double.class,
                "ST_Distance_Sphere(POINT({0}, {1}), POINT({2}, {3}))",
                traditionalMarket.lng, traditionalMarket.lat, lng, lat);

        List<Tuple> tuples = queryFactory
                .select(traditionalMarket, distance)
                .from(traditionalMarket)
                .where(
                        // 1단계: 바운딩박스 선필터 (idx_traditional_markets_lat_lng 활용)
                        traditionalMarket.lat.between(lat - latDelta, lat + latDelta),
                        traditionalMarket.lng.between(lng - lngDelta, lng + lngDelta),
                        // 2단계: 정밀 거리 필터
                        distance.loe(radiusMeters),
                        cursorCondition(cursorId, cursorDistance, distance)
                )
                .orderBy(distance.asc(), traditionalMarket.id.asc())
                .limit(size)
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

    /**
     * Keyset 기반 커서 조건: dist > c OR (dist = c AND id > cid).
     * 커서의 거리는 Double.toString() 전정밀도로 인코딩되므로, 파싱 후 Double.parseDouble에서
     * 정확히 복원된다. 따라서 distanceExpression.eq(cursorDistance)가 정밀하게 작동.
     */
    private BooleanExpression cursorCondition(
            Long cursorId, Double cursorDistance, NumberTemplate<Double> distanceExpression) {
        if (cursorId == null || cursorDistance == null) {
            return null;
        }
        // Keyset: dist > c OR (dist = c AND id > cid)
        // 경계행 중복 없음 (distanceExpression과 cursorDistance가 전정밀도 일치)
        return distanceExpression.gt(cursorDistance)
                .or(distanceExpression.eq(cursorDistance).and(traditionalMarket.id.gt(cursorId)));
    }
}
