package com.chunbaetour.domain.search.repository;

import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
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
        // blank 키워드 시 exactMatchScore = Expressions.asNumber(0) → ORDER BY 0 → MySQL ordinal 오류 방지
        var q = queryFactory.selectFrom(place)
                .where(placeNameContains(keyword), place.status.eq(PlaceStatus.ACTIVE))
                .limit(200);
        q = keyword != null && !keyword.isBlank()
                ? q.orderBy(exactMatchScore(place.name, keyword).desc(), place.id.desc())
                : q.orderBy(place.id.desc());
        return q.fetch();
    }

    public List<Shop> searchShops(String keyword) {
        var q = queryFactory.selectFrom(shop)
                .where(shopNameContains(keyword), shop.status.eq(ShopStatus.ACTIVE))
                .limit(200);
        q = keyword != null && !keyword.isBlank()
                ? q.orderBy(exactMatchScore(shop.shopName, keyword).desc(), shop.id.desc())
                : q.orderBy(shop.id.desc());
        return q.fetch();
    }

    public List<Menu> searchMenus(String keyword) {
        var q = queryFactory.selectFrom(menu)
                .where(menuNameContains(keyword), menu.isAvailable.isTrue())
                .limit(200);
        q = keyword != null && !keyword.isBlank()
                ? q.orderBy(exactMatchScore(menu.name, keyword).desc(), menu.id.desc())
                : q.orderBy(menu.id.desc());
        return q.fetch();
    }

    public List<Festival> searchFestivals(String keyword) {
        var q = queryFactory.selectFrom(festival)
                .where(festivalNameContains(keyword), festival.status.eq(FestivalStatus.ACTIVE))
                .limit(200);
        q = keyword != null && !keyword.isBlank()
                ? q.orderBy(exactMatchScore(festival.name, keyword).desc(), festival.id.desc())
                : q.orderBy(festival.id.desc());
        return q.fetch();
    }

    public List<TraditionalMarket> searchTraditionalMarkets(String keyword) {
        var q = queryFactory.selectFrom(traditionalMarket)
                .where(marketNameContains(keyword))
                .limit(200);
        q = keyword != null && !keyword.isBlank()
                ? q.orderBy(exactMatchScore(traditionalMarket.name, keyword).desc(), traditionalMarket.id.desc())
                : q.orderBy(traditionalMarket.id.desc());
        return q.fetch();
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

    private NumberExpression<Integer> exactMatchScore(StringPath path, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Expressions.asNumber(0);
        }
        return new CaseBuilder()
                .when(path.equalsIgnoreCase(keyword)).then(1)
                .otherwise(0);
    }
}

