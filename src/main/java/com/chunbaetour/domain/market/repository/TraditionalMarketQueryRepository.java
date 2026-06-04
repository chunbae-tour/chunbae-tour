package com.chunbaetour.domain.market.repository;

import com.chunbaetour.domain.market.entity.QTraditionalMarket;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyResponse;
import com.querydsl.core.types.Projections;
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
 * ST_Distance_Sphere를 이용한 위치 기반 검색 (Place 패턴 준용).
 */
@Repository
@RequiredArgsConstructor
public class TraditionalMarketQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 지정 좌표 기준 반경 내 전통시장 조회.
     * 거리 오름차순 정렬 + 커서 기반 페이지네이션.
     *
     * @param lat 위도 (요청 좌표)
     * @param lng 경도 (요청 좌표)
     * @param radiusMeters 반경 (미터)
     * @param cursorId 커서 ID (이전 페이지 마지막 시장 ID)
     * @param cursorDistance 커서 거리 (이전 페이지 마지막 시장과의 거리)
     * @param size 페이지 크기
     * @return 거리 오름차순 시장 목록
     */
    public List<TraditionalMarketNearbyResponse> findNearby(
            double lat, double lng, double radiusMeters,
            Long cursorId, Double cursorDistance, int size) {

        // MySQL ST_Distance_Sphere (반환: 미터 단위)
        // POINT(longitude, latitude) 순서 유의
        NumberTemplate<Double> distanceExpression = Expressions.numberTemplate(Double.class,
                "ST_Distance_Sphere(POINT({0}, {1}), POINT({2}, {3}))",
                traditionalMarket.lng, traditionalMarket.lat, lng, lat);

        return queryFactory
                .select(Projections.constructor(TraditionalMarketNearbyResponse.class,
                        traditionalMarket.id,
                        traditionalMarket.name,
                        traditionalMarket.address,
                        traditionalMarket.lat,
                        traditionalMarket.lng,
                        traditionalMarket.marketType,
                        distanceExpression,
                        Expressions.constant(null),  // imageUrl: null
                        Expressions.constant("TRADITIONAL_MARKET")  // targetType
                ))
                .from(traditionalMarket)
                .where(
                        distanceExpression.loe(radiusMeters),
                        cursorCondition(cursorId, cursorDistance, distanceExpression)
                )
                .orderBy(distanceExpression.asc(), traditionalMarket.id.asc())
                .limit(size)
                .fetch();
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
