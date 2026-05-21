package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.QPlace;
import com.chunbaetour.domain.place.dto.response.NearbyPlaceResponse;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.search.dto.response.SearchPlaceResponse;
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
                .or(distanceExpression.between(cursorDistance - 0.001, cursorDistance + 0.001).and(place.id.gt(cursorId)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 검색 (Search) 쿼리
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 관광지 키워드 검색 (Phase 2-2)
     * <p>
     * 커서 기반 페이지네이션을 지원하며, 기본 정렬은 placeId 내림차순(최신순)을 사용한다.
     * </p>
     * <p>
     * <b>[15년차 아키텍트 코멘트 - Tech Debt]</b><br>
     * 현재 {@code place.name.contains(keyword)}는 내부적으로 {@code LIKE '%keyword%'} 쿼리를 발생시킨다.
     * 이는 선행 와일드카드로 인해 MySQL의 B-Tree 인덱스를 타지 못하고 Full Table Scan을 유발한다.
     * 데이터가 적은 현재는 요구사항(LIKE %keyword%)을 충족하지만, 
     * 향후 데이터 증가 시 커스텀 Dialect를 등록하여 {@code MATCH(name) AGAINST(:keyword IN BOOLEAN MODE)} 방식의 
     * FULLTEXT 검색으로 리팩터링해야 한다.
     * </p>
     */
    public List<SearchPlaceResponse> searchByKeyword(String keyword, PlaceCategory category, String region, Long cursorId, int size) {
        return queryFactory
                .select(Projections.constructor(SearchPlaceResponse.class,
                        place.id,
                        place.name,
                        place.category,
                        place.address,
                        place.thumbnailUrl,
                        place.rating,
                        place.reviewCount
                ))
                .from(place)
                .where(
                        keywordContains(keyword),
                        categoryEq(category),
                        regionContains(region),
                        cursorConditionForSearch(cursorId),
                        place.status.eq(com.chunbaetour.domain.place.type.PlaceStatus.ACTIVE)
                )
                .orderBy(place.id.desc())
                .limit(size + 1) // hasNext 판단을 위해 1개 더 조회
                .fetch();
    }

    private BooleanExpression keywordContains(String keyword) {
        return org.springframework.util.StringUtils.hasText(keyword) ? place.name.contains(keyword) : null;
    }

    private BooleanExpression categoryEq(PlaceCategory category) {
        return category != null ? place.category.eq(category) : null;
    }

    private BooleanExpression regionContains(String region) {
        return org.springframework.util.StringUtils.hasText(region) ? place.address.contains(region) : null;
    }

    private BooleanExpression cursorConditionForSearch(Long cursorId) {
        return cursorId != null ? place.id.lt(cursorId) : null; // desc 정렬이므로 lt
    }
}

