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
     * 지정 좌표 기준 반경 내 전통시장 조회.
     * 거리 오름차순 정렬 + 커서 기반 페이지네이션.
     *
     * @param lat 위도
     * @param lng 경도
     * @param radiusMeters 반경
     * @param cursorId 커서 ID
     * @param cursorDistance 커서 거리
     * @param size 페이지 크기
     * @return 거리 오름차순 시장 목록
     */
    public List<TraditionalMarketNearbyResponse> findNearby(
            double lat, double lng, double radiusMeters,
            Long cursorId, Double cursorDistance, int size) {

        NumberTemplate<Double> distance = Expressions.numberTemplate(Double.class,
                "ST_Distance_Sphere(POINT({0}, {1}), POINT({2}, {3}))",
                traditionalMarket.lng, traditionalMarket.lat, lng, lat);

        List<Tuple> tuples = queryFactory
                .select(traditionalMarket, distance)
                .from(traditionalMarket)
                .where(
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
     * 커서 기반 페이지네이션 조건.
     * 거리 > 커서거리 || (거리 ≈ 커서거리 && id > 커서ID)
     */
    private BooleanExpression cursorCondition(
            Long cursorId, Double cursorDistance, NumberTemplate<Double> distanceExpression) {
        if (cursorId == null || cursorDistance == null) {
            return null;
        }
        // gt: 거리가 더 멂 (다음 페이지)
        // between + id.gt: 거리가 거의 같으면 id로 구분
        return distanceExpression.gt(cursorDistance)
                .or(distanceExpression.between(cursorDistance - 0.001, cursorDistance + 0.001)
                        .and(traditionalMarket.id.gt(cursorId)));
    }
}
