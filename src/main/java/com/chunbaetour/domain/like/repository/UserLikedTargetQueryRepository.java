package com.chunbaetour.domain.like.repository;

import static com.chunbaetour.domain.festival.entity.QFestival.festival;
import static com.chunbaetour.domain.like.entity.QUserLike.userLike;
import static com.chunbaetour.domain.market.entity.QTraditionalMarket.traditionalMarket;
import static com.chunbaetour.domain.place.QPlace.place;

import com.chunbaetour.domain.festival.type.FestivalStatus;
import com.chunbaetour.domain.like.dto.response.UserLikedTargetResponse;
import com.chunbaetour.domain.like.type.LikeTargetType;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

/**
 * QueryDSL repository for my-page liked target lists.
 *
 * <p>The {@code user_likes} table stores only target type/id, so each target type needs an explicit
 * join to expose card fields such as name, address, image, and likedAt.
 */
@Repository
@RequiredArgsConstructor
public class UserLikedTargetQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<UserLikedTargetResponse> findLikedPlaces(Long userId, Pageable pageable) {
        List<Tuple> tuples = queryFactory
                .select(place.id, place.name, place.category, place.address,
                        place.thumbnailUrl, place.rating, place.reviewCount, place.likeCount,
                        userLike.createdAt)
                .from(userLike)
                .join(place).on(userLike.targetId.eq(place.id))
                .where(
                        userLike.user.id.eq(userId),
                        userLike.targetType.eq(LikeTargetType.PLACE),
                        place.status.eq(PlaceStatus.ACTIVE)
                )
                .orderBy(orderSpecifiers(pageable).toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<UserLikedTargetResponse> content = tuples.stream()
                .map(tuple -> new UserLikedTargetResponse(
                        tuple.get(place.id),
                        LikeTargetType.PLACE,
                        tuple.get(place.name),
                        tuple.get(place.category) != null ? tuple.get(place.category).name() : null,
                        tuple.get(place.address),
                        tuple.get(place.thumbnailUrl),
                        placeRating(tuple.get(place.rating)),
                        tuple.get(place.reviewCount),
                        tuple.get(place.likeCount),
                        null,
                        null,
                        null,
                        tuple.get(userLike.createdAt)
                ))
                .toList();

        JPAQuery<Long> countQuery = queryFactory
                .select(userLike.count())
                .from(userLike)
                .join(place).on(userLike.targetId.eq(place.id))
                .where(
                        userLike.user.id.eq(userId),
                        userLike.targetType.eq(LikeTargetType.PLACE),
                        place.status.eq(PlaceStatus.ACTIVE)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    public Page<UserLikedTargetResponse> findLikedMarkets(Long userId, Pageable pageable) {
        List<Tuple> tuples = queryFactory
                .select(traditionalMarket.id, traditionalMarket.name, traditionalMarket.marketType,
                        traditionalMarket.address, traditionalMarket.sido, traditionalMarket.sigungu,
                        userLike.createdAt)
                .from(userLike)
                .join(traditionalMarket).on(userLike.targetId.eq(traditionalMarket.id))
                .where(
                        userLike.user.id.eq(userId),
                        userLike.targetType.eq(LikeTargetType.MARKET)
                )
                .orderBy(orderSpecifiers(pageable).toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<UserLikedTargetResponse> content = tuples.stream()
                .map(tuple -> new UserLikedTargetResponse(
                        tuple.get(traditionalMarket.id),
                        LikeTargetType.MARKET,
                        tuple.get(traditionalMarket.name),
                        tuple.get(traditionalMarket.marketType),
                        tuple.get(traditionalMarket.address),
                        null,
                        null,
                        null,
                        null,
                        marketRegion(tuple.get(traditionalMarket.sido), tuple.get(traditionalMarket.sigungu)),
                        null,
                        null,
                        tuple.get(userLike.createdAt)
                ))
                .toList();

        JPAQuery<Long> countQuery = queryFactory
                .select(userLike.count())
                .from(userLike)
                .join(traditionalMarket).on(userLike.targetId.eq(traditionalMarket.id))
                .where(
                        userLike.user.id.eq(userId),
                        userLike.targetType.eq(LikeTargetType.MARKET)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    public Page<UserLikedTargetResponse> findLikedFestivals(Long userId, Pageable pageable) {
        List<Tuple> tuples = queryFactory
                .select(festival.id, festival.name, festival.category, festival.address,
                        festival.imageUrl, festival.region, festival.startDate, festival.endDate,
                        userLike.createdAt)
                .from(userLike)
                .join(festival).on(userLike.targetId.eq(festival.id))
                .where(
                        userLike.user.id.eq(userId),
                        userLike.targetType.eq(LikeTargetType.FESTIVAL),
                        festival.status.eq(FestivalStatus.ACTIVE)
                )
                .orderBy(orderSpecifiers(pageable).toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<UserLikedTargetResponse> content = tuples.stream()
                .map(tuple -> new UserLikedTargetResponse(
                        tuple.get(festival.id),
                        LikeTargetType.FESTIVAL,
                        tuple.get(festival.name),
                        tuple.get(festival.category) != null ? tuple.get(festival.category).name() : null,
                        tuple.get(festival.address),
                        tuple.get(festival.imageUrl),
                        null,
                        null,
                        null,
                        tuple.get(festival.region),
                        tuple.get(festival.startDate),
                        tuple.get(festival.endDate),
                        tuple.get(userLike.createdAt)
                ))
                .toList();

        JPAQuery<Long> countQuery = queryFactory
                .select(userLike.count())
                .from(userLike)
                .join(festival).on(userLike.targetId.eq(festival.id))
                .where(
                        userLike.user.id.eq(userId),
                        userLike.targetType.eq(LikeTargetType.FESTIVAL),
                        festival.status.eq(FestivalStatus.ACTIVE)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private List<OrderSpecifier<?>> orderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            com.querydsl.core.types.Order direction = order.isAscending()
                    ? com.querydsl.core.types.Order.ASC
                    : com.querydsl.core.types.Order.DESC;
            if ("createdAt".equals(order.getProperty())) {
                orderSpecifiers.add(new OrderSpecifier<>(direction, userLike.createdAt));
            } else if ("id".equals(order.getProperty())) {
                orderSpecifiers.add(new OrderSpecifier<>(direction, userLike.id));
            }
        }
        orderSpecifiers.add(userLike.createdAt.desc());
        orderSpecifiers.add(userLike.id.desc());
        return orderSpecifiers;
    }

    private String marketRegion(String sido, String sigungu) {
        if (sido == null || sido.isBlank()) {
            return sigungu;
        }
        if (sigungu == null || sigungu.isBlank()) {
            return sido;
        }
        return sido + " " + sigungu;
    }

    private Float placeRating(Integer storedRating) {
        return storedRating != null ? storedRating / 10.0f : null;
    }
}
