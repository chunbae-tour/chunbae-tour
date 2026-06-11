package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.dto.response.NearbyPlaceResponse;
import com.chunbaetour.domain.place.dto.response.PlaceListResponse.PlaceListItem;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.search.dto.response.SearchPlaceResponse;
import com.chunbaetour.domain.place.util.LocationUtils;
import com.chunbaetour.domain.place.dto.response.MapMarkerResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberTemplate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.chunbaetour.domain.place.QPlace.place;
import static com.chunbaetour.domain.like.entity.QUserLike.userLike;
import com.chunbaetour.domain.like.type.LikeTargetType;
import com.chunbaetour.domain.place.dto.response.UserLikedPlaceResponse;
import com.querydsl.jpa.impl.JPAQuery;

@Repository
@RequiredArgsConstructor
public class PlaceQueryRepository {

    private final JPAQueryFactory queryFactory;

    // ──────────────────────────────────────────────────────────────────────────
    // 마이페이지 찜한 관광지 조회 (QueryDSL JOIN 최적화)
    // ──────────────────────────────────────────────────────────────────────────
    public Page<UserLikedPlaceResponse> findUserLikedPlaces(Long userId, Pageable pageable) {
        List<com.querydsl.core.types.OrderSpecifier<?>> orderSpecifiers = new java.util.ArrayList<>();
        for (org.springframework.data.domain.Sort.Order order : pageable.getSort()) {
            com.querydsl.core.types.Order direction = order.isAscending() ? com.querydsl.core.types.Order.ASC : com.querydsl.core.types.Order.DESC;
            if ("createdAt".equals(order.getProperty())) {
                orderSpecifiers.add(new com.querydsl.core.types.OrderSpecifier<>(direction, userLike.createdAt));
            } else if ("id".equals(order.getProperty())) {
                orderSpecifiers.add(new com.querydsl.core.types.OrderSpecifier<>(direction, userLike.id));
            }
        }
        // 안정적인 페이징을 위한 Tie-breaker (기본 정렬)
        orderSpecifiers.add(userLike.createdAt.desc());
        orderSpecifiers.add(userLike.id.desc());

        List<com.querydsl.core.Tuple> tuples = queryFactory
                .select(place, userLike.createdAt)
                .from(userLike)
                .join(place).on(userLike.targetId.eq(place.id))
                .where(userLike.user.id.eq(userId),
                       userLike.targetType.eq(LikeTargetType.PLACE),
                       place.status.eq(PlaceStatus.ACTIVE))
                .orderBy(orderSpecifiers.toArray(new com.querydsl.core.types.OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<UserLikedPlaceResponse> content = tuples.stream()
                .map(t -> UserLikedPlaceResponse.from(t.get(place), t.get(userLike.createdAt)))
                .toList();

        JPAQuery<Long> countQuery = queryFactory
                .select(userLike.count())
                .from(userLike)
                .join(place).on(userLike.targetId.eq(place.id))
                .where(userLike.user.id.eq(userId),
                       userLike.targetType.eq(LikeTargetType.PLACE),
                       place.status.eq(PlaceStatus.ACTIVE));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 위치 기반 근처 관광지 (Nearby) 쿼리
    // ──────────────────────────────────────────────────────────────────────────

    public List<NearbyPlaceResponse> findNearbyPlaces(double lat, double lng, double radiusMeters, Long cursorId, Double cursorDistance, int size) {
        String mbrPolygon = com.chunbaetour.domain.place.util.LocationUtils.calculateMbrPolygon(lat, lng, radiusMeters);

        NumberTemplate<Double> distanceExpression = Expressions.numberTemplate(Double.class,
                "ST_Distance_Sphere({0}, ST_GeomFromText({1}, 4326, 'axis-order=long-lat'))",
                place.location, String.format(java.util.Locale.US, "POINT(%f %f)", lng, lat));

        return queryFactory
                .select(Projections.constructor(NearbyPlaceResponse.class,
                        place.id,
                        place.name,
                        place.category,
                        place.thumbnailUrl,
                        Expressions.numberTemplate(Double.class, "ST_Y({0})", place.location),
                        Expressions.numberTemplate(Double.class, "ST_X({0})", place.location),
                        place.rating, // rating은 내부적으로 int이지만 QueryDSL이 Projections로 매핑할 때 DTO 생성자를 탐색합니다.
                        place.reviewCount,
                        distanceExpression
                ))
                .from(place)
                .where(
                        Expressions.numberTemplate(Integer.class, "MBRContains(ST_GeomFromText({0}, 4326, 'axis-order=long-lat'), {1})", mbrPolygon, place.location).eq(1),
                        distanceExpression.loe(radiusMeters),
                        place.status.eq(PlaceStatus.ACTIVE),
                        cursorConditionForNearby(cursorId, cursorDistance, distanceExpression)
                )
                .orderBy(distanceExpression.asc(), place.id.asc())
                .limit(size + 1)
                .fetch();
    }

    private BooleanExpression cursorConditionForNearby(Long cursorId, Double cursorDistance, NumberTemplate<Double> distanceExpression) {
        if (cursorId == null || cursorDistance == null) {
            return null;
        }
        // 부동 소수점 오차를 고려해 eq 대신 between 사용
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
     * <b>[15년차 아키텍트 코멘트]</b><br>
     * 기존의 {@code place.name.contains(keyword)} (LIKE '%keyword%') 방식에서 발생하는 Full Table Scan 병목을 해결하기 위해,
     * KAN-239를 통해 MySQL ngram 파서를 적용한 FULLTEXT 인덱스(Boolean Mode) 검색으로 리팩터링되었습니다.
     * 1글자 검색 등 인덱스를 탈 수 없는 특정 예외 케이스에 대해서만 부분적으로 LIKE 검색으로 우회(Fallback)합니다.
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
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        
        // ngram_token_size 기본값이 2이므로 1글자 검색은 인덱스를 타지 못함. LIKE 검색으로 우회 (Fallback)
        if (keyword.trim().length() == 1) {
            return place.name.contains(keyword.trim());
        }

        String formattedKeyword = formatForBooleanMode(keyword);
        
        // 특수문자만 입력되어 유효한 검색 토큰이 없는 경우, 결과를 반환하지 않음
        if (formattedKeyword == null) {
            return Expressions.FALSE;
        }

        // MATCH(name) AGAINST(:formattedKeyword IN BOOLEAN MODE) > 0
        return Expressions.numberTemplate(Double.class, "function('match_against', {0}, {1})", place.name, formattedKeyword).gt(0.0);
    }

    /**
     * 사용자의 검색어를 Boolean Mode 검색에 맞게 변환합니다.
     * MySQL Boolean Mode 연산자(+, -, *, ", (, ) 등) 충돌을 방지하기 위해 정규식을 사용하여 안전한 문자만 남깁니다.
     * ngram 파서를 사용하므로 와일드카드(*)는 무의미하여 제거합니다.
     * 예: "제주 카페" -> "+제주 +카페"
     */
    protected String formatForBooleanMode(String keyword) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[\\p{L}\\p{N}]+").matcher(keyword);
        java.util.List<String> tokens = new java.util.ArrayList<>();

        while (matcher.find()) {
            tokens.add("+" + matcher.group());
        }

        if (tokens.isEmpty()) {
            return null;
        }

        return String.join(" ", tokens);
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

    /**
     * 개인화 관광지 검색 (Phase 9-2) — 선호 카테고리 부스팅 정렬.
     * <p>
     * 기본 검색({@link #searchByKeyword})과 동일한 필터 조건을 사용하되,
     * {@code preferredCategories}에 포함된 카테고리를 최상단에 노출한다.
     * </p>
     *
     * <b>[정렬 전략]</b><br>
     * QueryDSL {@code orderBy}에 {@code CASE WHEN} 표현식을 추가하여
     * 선호 카테고리의 결과를 {@code priority = 0}, 나머지를 {@code priority = 1}로
     * 분리하고, 각 그룹 내에서는 {@code place.id DESC} 기본 정렬을 유지한다.
     * </p>
     *
     * @param keyword             검색어 (null 허용)
     * @param category            카테고리 필터 (null → 전체)
     * @param region              지역 필터 (null → 전체)
     * @param cursorId            커서용 마지막 placeId (null → 첫 페이지)
     * @param size                페이지 사이즈
     * @param preferredCategories 선호 카테고리 목록 (빈 리스트 → 기본 정렬)
     * @return 선호 카테고리가 최상단에 배치된 관광지 검색 결과
     */
    public List<SearchPlaceResponse> searchByKeywordWithPersonalization(
            String keyword, PlaceCategory category, String region,
            Long cursorId, int size, List<PlaceCategory> preferredCategories) {

        // 선호 카테고리가 없으면 기본 검색으로 위임 (불필요한 CASE WHEN 회피)
        if (preferredCategories == null || preferredCategories.isEmpty()) {
            return searchByKeyword(keyword, category, region, cursorId, size);
        }

        // CASE WHEN p.category IN (:preferredCategories) THEN 0 ELSE 1 END
        // 선호 카테고리 → priority 0 (최상단), 나머지 → priority 1
        com.querydsl.core.types.dsl.NumberExpression<Integer> priorityExpr =
                new com.querydsl.core.types.dsl.CaseBuilder()
                        .when(place.category.in(preferredCategories))
                        .then(0)
                        .otherwise(1);

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
                // 1차: 선호 카테고리 우선(0), 2차: place.id 내림차순(최신순)
                .orderBy(priorityExpr.asc(), place.id.desc())
                .limit(size + 1) // hasNext 판단을 위해 1개 더 조회
                .fetch();
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
    /**
     * 지도 마커 일괄 조회 (PHASE 8-1)
     * <p>
     * 주어진 Bounding Box 영역(SW 좌표 ~ NE 좌표) 내에 포함된 활성 상태의 관광지 마커들을 조회합니다.
     * MySQL의 MBRContains 함수와 ST_GeomFromText를 활용하여 공간 인덱스(R-Tree)를 태웁니다.
     * 프론트엔드 렌더링 성능과 서버 OOM을 방지하기 위해 최대 500개로 제한합니다.
     * </p>
     *
     * @param swLat 남서쪽 위도 (최소 Y)
     * @param swLng 남서쪽 경도 (최소 X)
     * @param neLat 북동쪽 위도 (최대 Y)
     * @param neLng 북동쪽 경도 (최대 X)
     * @return 뷰포트 내의 지도 마커 리스트 (최대 500개)
     */
    public List<MapMarkerResponse> findMarkersInBoundingBox(double swLat, double swLng, double neLat, double neLng) {
        // MySQL ST_GeomFromText Polygon 생성 (LocationUtils 헬퍼 사용)
        String mbrPolygon = LocationUtils.calculateMbrPolygon(swLat, swLng, neLat, neLng);

        return queryFactory
                .select(Projections.constructor(MapMarkerResponse.class,
                        place.id,
                        place.name,
                        place.category,
                        Expressions.numberTemplate(Double.class, "ST_Y({0})", place.location),
                        Expressions.numberTemplate(Double.class, "ST_X({0})", place.location),
                        place.thumbnailUrl
                ))
                .from(place)
                .where(
                        Expressions.numberTemplate(Integer.class, "MBRContains(ST_GeomFromText({0}, 4326, 'axis-order=long-lat'), {1})", mbrPolygon, place.location).eq(1),
                        place.status.eq(PlaceStatus.ACTIVE)
                )
                .orderBy(place.id.desc())
                .limit(501) // 501개를 조회하여 더 있는지(truncated) 여부 판단
                .fetch();
    }
}
