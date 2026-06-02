package com.chunbaetour.domain.search.repository;

import com.chunbaetour.domain.festival.entity.Festival;
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
import static com.chunbaetour.domain.place.QPlace.place;
import static com.chunbaetour.domain.shop.entity.QMenu.menu;
import static com.chunbaetour.domain.shop.entity.QShop.shop;

@Repository
@RequiredArgsConstructor
public class SearchQueryRepository {
    
    private final JPAQueryFactory queryFactory;

    public List<Place> searchPlaces(String keyword) {
        return queryFactory
                .selectFrom(place)
                .where(
                        placeNameContains(keyword),
                        place.status.eq(PlaceStatus.ACTIVE)
                )
                .orderBy(exactMatchScore(place.name, keyword).desc(), place.id.desc())
                .limit(200)
                .fetch();
    }

    public List<Shop> searchShops(String keyword) {
        return queryFactory
                .selectFrom(shop)
                .where(
                        shopNameContains(keyword),
                        shop.status.eq(ShopStatus.ACTIVE)
                )
                .orderBy(exactMatchScore(shop.shopName, keyword).desc(), shop.id.desc())
                .limit(200)
                .fetch();
    }

    public List<Menu> searchMenus(String keyword) {
        return queryFactory
                .selectFrom(menu)
                .where(
                        menuNameContains(keyword),
                        menu.isAvailable.isTrue()
                )
                .orderBy(exactMatchScore(menu.name, keyword).desc(), menu.id.desc())
                .limit(200)
                .fetch();
    }

    public List<Festival> searchFestivals(String keyword) {
        return queryFactory
                .selectFrom(festival)
                .where(
                        festivalNameContains(keyword),
                        festival.status.eq(FestivalStatus.ACTIVE)
                )
                .orderBy(exactMatchScore(festival.name, keyword).desc(), festival.id.desc())
                .limit(200)
                .fetch();
    }

    private BooleanExpression placeNameContains(String keyword) {
        return keyword != null && !keyword.isBlank() ? place.name.containsIgnoreCase(keyword) : null;
    }

    private BooleanExpression shopNameContains(String keyword) {
        return keyword != null && !keyword.isBlank() ? shop.shopName.containsIgnoreCase(keyword) : null;
    }

    private BooleanExpression menuNameContains(String keyword) {
        return keyword != null && !keyword.isBlank() ? menu.name.containsIgnoreCase(keyword) : null;
    }

    private BooleanExpression festivalNameContains(String keyword) {
        return keyword != null && !keyword.isBlank() ? festival.name.containsIgnoreCase(keyword) : null;
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

