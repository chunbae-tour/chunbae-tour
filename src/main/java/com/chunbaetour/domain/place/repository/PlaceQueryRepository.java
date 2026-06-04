package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.QPlace;
import com.chunbaetour.domain.place.dto.response.NearbyPlaceResponse;
import com.chunbaetour.domain.place.dto.response.PlaceListResponse.PlaceListItem;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.search.dto.response.SearchPlaceResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberTemplate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

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

    // ──────────────────────────────────────────────────────────────────────────
    // 자동완성 (Suggest) 쿼리
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 관광지명 prefix 자동완성 (Phase 2-4).
     * <p>
     * {@code LIKE 'prefix%'} 패턴을 사용하므로 선행 와일드카드가 없어
     * {@code idx_places_name} B-Tree 인덱스의 Range Scan이 가능하다.
     * (※ {@code LIKE '%keyword%'} 방식인 keywordContains와 성능 특성이 다름)
     * </p>
     * <p>
     * ACTIVE 상태 관광지만 대상으로 하여 노출 중단된 장소가 자동완성에 노출되지 않도록 한다.
     * </p>
     *
     * @param prefix 사용자가 입력한 prefix (검색창 입력 중인 문자열, non-null/non-blank)
     * @param limit  최대 반환 건수 (SA 명세 기준 최대 5)
     * @return place.name 목록 (최대 limit 건)
     */
    public List<String> suggestByPrefix(String prefix, int limit) {
        // _, % 와일드카드 이스케이프 처리하여 의도치 않은 임의 문자 매칭 방지
        String safePrefix = prefix.replace("\\", "\\\\")
                                  .replace("_", "\\_")
                                  .replace("%", "\\%");

        return queryFactory
                .select(place.name)
                .from(place)
                .where(
                        // LIKE 'safePrefix%' — 후행 와일드카드만 사용하므로 B-Tree 인덱스 Range Scan 가능
                        place.name.startsWith(safePrefix),
                        // 노출 중단/삭제된 관광지는 자동완성에서 제외
                        place.status.eq(com.chunbaetour.domain.place.type.PlaceStatus.ACTIVE)
                )
                // 이름 오름차순: 자동완성은 사전순이 사용자 경험에 더 자연스러움
                .orderBy(place.name.asc())
                .limit(limit)
                .fetch();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 목록 조회 (List) 쿼리 — PHASE 8-2
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 관광지 목록 조회 (카테고리/지역 필터 + 커서 기반 페이지네이션).
     *
     * <p>정렬 기준: 평점 내림차순({@code rating DESC}) → ID 내림차순({@code id DESC}).
     * 평점이 같은 데이터가 많을 수 있어 ID를 2차 정렬 키로 사용하여 커서 안정성을 보장합니다.
     *
     * <p>커서 방식: ID 기반 ({@code id < cursorId}).
     * 평점이 같은 경우에도 정렬 순서가 보장되도록 동일 평점 내에서 ID 내림차순을 사용하므로,
     * 단순 {@code id < cursorId} 조건이 유효합니다.
     *
     * <p>hasNext 판단을 위해 {@code size + 1}건을 조회하므로 호출부에서 마지막 요소를 제거해야 합니다.
     *
     * @param category   카테고리 필터 (null 허용 — null이면 전체 카테고리)
     * @param region     지역 필터 (null 허용 — null이면 전체 지역, {@code address LIKE '%region%'} 방식)
     * @param cursorId   이전 페이지 마지막 관광지의 ID (null이면 첫 페이지)
     * @param size       한 페이지 최대 건수 (hasNext 판단을 위해 호출부에서 size + 1 전달)
     * @return           PlaceListItem 목록
     */
    public List<PlaceListItem> findByFilter(PlaceCategory category, String region, Long cursorId, int size) {
        return queryFactory
                .select(Projections.constructor(PlaceListItem.class,
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
                        place.status.eq(com.chunbaetour.domain.place.type.PlaceStatus.ACTIVE),
                        categoryFilter(category),
                        regionFilter(region),
                        cursorConditionForList(cursorId)
                )
                // 1차: 평점 높은 순, 2차: ID 내림차순 (최신 등록 우선, 커서 안정성 보장)
                .orderBy(place.rating.desc(), place.id.desc())
                .limit(size)
                .fetch();
    }

    /** 카테고리 필터 — null이면 조건 생략 (전체 카테고리) */
    private BooleanExpression categoryFilter(PlaceCategory category) {
        return category != null ? place.category.eq(category) : null;
    }

    /** 지역 필터 — null 또는 공백이면 조건 생략. {@code address LIKE '%region%'} 방식이라 인덱스 미사용 주의. */
    private BooleanExpression regionFilter(String region) {
        return StringUtils.hasText(region) ? place.address.contains(region) : null;
    }

    /**
     * 목록 조회용 커서 조건 — ID 내림차순 정렬에서 다음 페이지를 가져오기 위해 {@code id < cursorId}.
     * cursorId가 null이면 첫 페이지 조회로 간주하여 조건 생략.
     */
    private BooleanExpression cursorConditionForList(Long cursorId) {
        return cursorId != null ? place.id.lt(cursorId) : null;
    }
}
