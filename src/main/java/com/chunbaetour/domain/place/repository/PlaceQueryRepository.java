package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.dto.response.NearbyPlaceResponse;
import com.chunbaetour.domain.place.dto.response.PlaceListResponse.PlaceListItem;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
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

    // ──────────────────────────────────────────────────────────────────────────
    // 위치 기반 근처 관광지 (Nearby) 쿼리
    // ──────────────────────────────────────────────────────────────────────────

    public List<NearbyPlaceResponse> findNearbyPlaces(double lat, double lng, double radiusMeters) {
        // MBR 박스 계산 (1도 당 약 111km 가정, 반경에 따른 대략적인 사각형)
        double latDegree = radiusMeters / 111000.0;
        double lngDegree = radiusMeters / (111000.0 * Math.cos(Math.toRadians(lat)));
        
        String mbrPolygon = String.format("POLYGON((%f %f, %f %f, %f %f, %f %f, %f %f))",
                lng - lngDegree, lat - latDegree,
                lng + lngDegree, lat - latDegree,
                lng + lngDegree, lat + latDegree,
                lng - lngDegree, lat + latDegree,
                lng - lngDegree, lat - latDegree);

        NumberTemplate<Double> distanceExpression = Expressions.numberTemplate(Double.class,
                "ST_Distance_Sphere({0}, ST_GeomFromText({1}, 4326, 'axis-order=long-lat'))",
                place.location, String.format("POINT(%f %f)", lng, lat));

        return queryFactory
                .select(Projections.constructor(NearbyPlaceResponse.class,
                        place.id,
                        place.name,
                        place.category,
                        place.thumbnailUrl,
                        Expressions.numberTemplate(java.math.BigDecimal.class, "ST_Y({0})", place.location),
                        Expressions.numberTemplate(java.math.BigDecimal.class, "ST_X({0})", place.location),
                        place.rating, // rating은 내부적으로 int이지만 QueryDSL이 Projections로 매핑할 때 DTO 생성자를 탐색합니다.
                        place.reviewCount,
                        distanceExpression
                ))
                .from(place)
                .where(
                        Expressions.booleanTemplate("MBRContains(ST_GeomFromText({0}, 4326, 'axis-order=long-lat'), {1})", mbrPolygon, place.location),
                        distanceExpression.loe(radiusMeters),
                        place.status.eq(PlaceStatus.ACTIVE)
                )
                .orderBy(distanceExpression.asc(), place.id.asc())
                .fetch();
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
    public List<SearchPlaceResponse> searchByKeyword(String keyword, PlaceCategory category,
                                                      String region, Long cursorId, int size) {
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
                        place.status.eq(PlaceStatus.ACTIVE)
                )
                .orderBy(place.id.desc())
                .limit(size + 1) // hasNext 판단을 위해 1개 더 조회
                .fetch();
    }

    private BooleanExpression keywordContains(String keyword) {
        return StringUtils.hasText(keyword) ? place.name.contains(keyword) : null;
    }

    private BooleanExpression categoryEq(PlaceCategory category) {
        return category != null ? place.category.eq(category) : null;
    }

    private BooleanExpression regionContains(String region) {
        return StringUtils.hasText(region) ? place.address.contains(region) : null;
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
                        place.status.eq(PlaceStatus.ACTIVE)
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
     * 관광지 목록 조회 (카테고리/지역 필터 + 복합 커서 기반 페이지네이션).
     *
     * <p>정렬 기준: 평점 내림차순({@code rating DESC}) → ID 내림차순({@code id DESC}).
     * 평점이 같은 데이터가 많을 수 있어 ID를 2차 정렬 키로 사용하여 커서 안정성을 보장합니다.
     *
     * <p><b>[복합 커서 설계]</b><br>
     * 단순 {@code id < cursorId} 조건은 평점이 다른 경계에서 데이터 누락/중복을 유발합니다.
     * 올바른 조건: {@code (rating < cursorRating) OR (rating = cursorRating AND id < cursorId)}
     *
     * <p>hasNext 판단을 위해 {@code size + 1}건을 조회합니다. 호출부(Service)에서 마지막 요소를 제거합니다.
     *
     * @param category     카테고리 필터 (null → 전체)
     * @param region       지역 필터 (null → 전체, LIKE '%region%' 방식)
     * @param cursorId     이전 페이지 마지막 ID (null → 첫 페이지)
     * @param cursorRating 이전 페이지 마지막 평점 (null → 첫 페이지)
     * @param size         조회 건수 (hasNext 판단용으로 size+1 전달 권장)
     * @return PlaceListItem 목록
     */
    public List<PlaceListItem> findByFilter(PlaceCategory category, String region,
                                             Long cursorId, Float cursorRating, int size) {
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
                        place.status.eq(PlaceStatus.ACTIVE),
                        categoryFilter(category),
                        regionFilter(region),
                        cursorConditionForList(cursorId, cursorRating)
                )
                // 1차: 평점 높은 순, 2차: ID 내림차순 (커서 안정성 보장)
                .orderBy(place.rating.desc(), place.id.desc())
                .limit(size + 1)
                .fetch();
    }

    /** 카테고리 필터 — null이면 조건 생략 (전체 카테고리) */
    private BooleanExpression categoryFilter(PlaceCategory category) {
        return category != null ? place.category.eq(category) : null;
    }

    /** 지역 필터 — null 또는 공백이면 조건 생략. address LIKE '%region%' 방식이라 인덱스 미사용 주의. */
    private BooleanExpression regionFilter(String region) {
        return StringUtils.hasText(region) ? place.address.contains(region) : null;
    }

    /**
     * 목록 조회용 복합 커서 조건.
     *
     * <p>정렬이 {@code rating DESC, id DESC}이므로 올바른 커서 조건:
     * <pre>
     *   (rating &lt; cursorRating)
     *   OR (rating = cursorRating AND id &lt; cursorId)
     * </pre>
     * 단순 {@code id < cursorId}만 사용하면 평점이 다른 행이 누락되거나 중복될 수 있습니다.
     *
     * @param cursorId     이전 페이지 마지막 ID (null이면 첫 페이지)
     * @param cursorRating 이전 페이지 마지막 평점 (null이면 첫 페이지)
     */
    private BooleanExpression cursorConditionForList(Long cursorId, Float cursorRating) {
        if (cursorId == null || cursorRating == null) {
            return null; // 첫 페이지 — 커서 조건 없음
        }
        // DB의 rating은 Float 값의 10배인 Integer로 저장되어 있음
        int cursorRatingInt = Math.round(cursorRating * 10);
        // (rating < cursorRating) OR (rating = cursorRating AND id < cursorId)
        return place.rating.lt(cursorRatingInt)
                .or(place.rating.eq(cursorRatingInt).and(place.id.lt(cursorId)));
    }
}
