package com.chunbaetour.domain.search.repository;

import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.chunbaetour.domain.festival.entity.QFestival.festival;
import static com.chunbaetour.domain.market.entity.QTraditionalMarket.traditionalMarket;
import static com.chunbaetour.domain.place.QPlace.place;
import static com.chunbaetour.domain.shop.entity.QMenu.menu;
import static com.chunbaetour.domain.shop.entity.QShop.shop;

@Repository
@RequiredArgsConstructor
public class SearchQueryRepository {
    
    private final JPAQueryFactory queryFactory;

    // [기술 부채(Technical Debt) 명시]
    // 현재 containsIgnoreCase()는 SQL 상에서 LIKE '%keyword%'로 변환되어 B-tree 인덱스를 타지 못하고 Full Table Scan을 유발합니다.
    // MVP 단계에서는 limit(200) 하드 캡을 통해 메모리 부하를 방어하고 있으나, 데이터가 커질수록 DB 쿼리 자체가 느려질 위험이 있습니다.
    // 단기적으로는 각 name 컬럼에 Full-Text Index(MATCH ... AGAINST)를 생성하거나, 중장기적으로 Elasticsearch 등 전문 검색 엔진 도입이 필요합니다.

    public List<Place> searchPlaces(String keyword) {
        return queryFactory.selectFrom(place)
                .where(placeNameContains(keyword), place.status.eq(PlaceStatus.ACTIVE))
                .orderBy(buildOrderBy(place.name, place.id, keyword))
                .limit(200)
                .fetch();
    }

    public List<Shop> searchShops(String keyword) {
        return queryFactory.selectFrom(shop)
                .where(shopNameContains(keyword), shop.status.eq(ShopStatus.ACTIVE))
                .orderBy(buildOrderBy(shop.shopName, shop.id, keyword))
                .limit(200)
                .fetch();
    }

    public List<Menu> searchMenus(String keyword) {
        return queryFactory.selectFrom(menu)
                .where(menuNameContains(keyword), menu.isAvailable.isTrue())
                .orderBy(buildOrderBy(menu.name, menu.id, keyword))
                .limit(200)
                .fetch();
    }

    public List<Festival> searchFestivals(String keyword) {
        return queryFactory.selectFrom(festival)
                .where(festivalNameContains(keyword), festival.status.eq(FestivalStatus.ACTIVE))
                .orderBy(buildOrderBy(festival.name, festival.id, keyword))
                .limit(200)
                .fetch();
    }

    public List<TraditionalMarket> searchTraditionalMarkets(String keyword) {
        return queryFactory.selectFrom(traditionalMarket)
                .where(marketNameContains(keyword))
                .orderBy(buildOrderBy(traditionalMarket.name, traditionalMarket.id, keyword))
                .limit(200)
                .fetch();
    }

    private BooleanExpression placeNameContains(String keyword) {
        return keyword != null && !keyword.isBlank() ? place.name.containsIgnoreCase(keyword) : Expressions.FALSE;
    }

    private BooleanExpression shopNameContains(String keyword) {
        return keyword != null && !keyword.isBlank() ? shop.shopName.containsIgnoreCase(keyword) : Expressions.FALSE;
    }

    private BooleanExpression menuNameContains(String keyword) {
        return keyword != null && !keyword.isBlank() ? menu.name.containsIgnoreCase(keyword) : Expressions.FALSE;
    }

    private BooleanExpression festivalNameContains(String keyword) {
        return keyword != null && !keyword.isBlank() ? festival.name.containsIgnoreCase(keyword) : Expressions.FALSE;
    }

    private BooleanExpression marketNameContains(String keyword) {
        return keyword != null && !keyword.isBlank() ? traditionalMarket.name.containsIgnoreCase(keyword) : Expressions.FALSE;
    }

    /**
     * blank 키워드 시 exactMatchScore = Expressions.asNumber(0) → ORDER BY 0 → MySQL ordinal 오류 방지.
     * blank면 id.desc() 단순 정렬, 유효 키워드면 정확도순 + id순.
     */
    @SuppressWarnings("unchecked")
    private OrderSpecifier<?>[] buildOrderBy(StringPath namePath, NumberPath<Long> idPath, String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return new OrderSpecifier[]{exactMatchScore(namePath, keyword).desc(), idPath.desc()};
        }
        return new OrderSpecifier[]{idPath.desc()};
    }

    private NumberExpression<Integer> exactMatchScore(StringPath path, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Expressions.asNumber(0);
        }
        return new CaseBuilder()
                .when(path.equalsIgnoreCase(keyword)).then(1)
                .otherwise(0);
    }
}

