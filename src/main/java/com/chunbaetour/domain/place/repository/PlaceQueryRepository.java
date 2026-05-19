package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.QPlace;
import com.chunbaetour.domain.place.dto.response.NearbyPlaceResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberTemplate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.chunbaetour.domain.place.QPlace.place;

@Repository
@RequiredArgsConstructor
public class PlaceQueryRepository {
    
    private final JPAQueryFactory queryFactory;

    public List<NearbyPlaceResponse> findNearbyPlaces(double lat, double lng, double radiusMeters, Long cursorId, Double cursorDistance, int size) {
        
        // MySQL ST_Distance_Sphere (returns meters). Arguments for POINT are (longitude, latitude)
        NumberTemplate<Double> distanceExpression = Expressions.numberTemplate(Double.class,
                "ST_Distance_Sphere(POINT({0}, {1}), POINT({2}, {3}))",
                place.lng, place.lat, lng, lat);

        return queryFactory
                .select(Projections.constructor(NearbyPlaceResponse.class,
                        place.id,
                        place.name,
                        place.category,
                        place.thumbnailUrl,
                        place.lat,
                        place.lng,
                        place.rating,
                        place.reviewCount,
                        distanceExpression
                ))
                .from(place)
                .where(
                        distanceExpression.loe(radiusMeters),
                        cursorCondition(cursorId, cursorDistance, distanceExpression),
                        place.status.eq(com.chunbaetour.domain.place.type.PlaceStatus.ACTIVE)
                )
                .orderBy(distanceExpression.asc(), place.id.asc())
                .limit(size)
                .fetch();
    }

    private BooleanExpression cursorCondition(Long cursorId, Double cursorDistance, NumberTemplate<Double> distanceExpression) {
        if (cursorId == null || cursorDistance == null) {
            return null;
        }
        return distanceExpression.gt(cursorDistance)
                .or(distanceExpression.eq(cursorDistance).and(place.id.gt(cursorId)));
    }
}

