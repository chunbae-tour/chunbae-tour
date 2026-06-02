package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.search.dto.request.IntegratedSearchCursor;
import com.chunbaetour.domain.search.dto.response.integrated.*;
import com.chunbaetour.domain.search.repository.SearchQueryRepository;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntegratedSearchService {

    private final SearchQueryRepository searchQueryRepository;
    private static final int FETCH_LIMIT = 100;

    public CursorPageResponse<IntegratedSearchItem> searchIntegrated(String keyword, String type, String cursorStr, int size) {
        IntegratedSearchCursor cursor = IntegratedSearchCursor.decode(cursorStr);
        List<ItemWrapper> allItems = new ArrayList<>();
        boolean searchAll = "ALL".equalsIgnoreCase(type);
        
        // 1. PLACE
        if (searchAll || "PLACE".equalsIgnoreCase(type)) {
            List<Place> places = searchQueryRepository.searchPlaces(keyword, FETCH_LIMIT);
            for (Place p : places) {
                double score = calculateScore(p.getName(), keyword);
                IntegratedPlaceItem item = IntegratedPlaceItem.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .category(p.getCategory())
                        .address(p.getAddress())
                        .thumbnailUrl(p.getThumbnailUrl())
                        .matchedShopCount(0)
                        .matchedMenuNames(Collections.emptyList())
                        .build();
                allItems.add(new ItemWrapper(score, 100, "PLACE", p.getId(), item));
            }
        }

        // 2. SHOP
        if (searchAll || "SHOP".equalsIgnoreCase(type)) {
            List<Shop> shops = searchQueryRepository.searchShops(keyword, FETCH_LIMIT);
            for (Shop s : shops) {
                double score = calculateScore(s.getShopName(), keyword);
                IntegratedShopItem item = IntegratedShopItem.builder()
                        .id(s.getId())
                        .shopId(s.getId())
                        .name(s.getShopName())
                        .placeId(null)
                        .placeName(null) // 연관관계 없음 (MVP)
                        .address(s.getAddress())
                        .thumbnailUrl(null)
                        .rating(s.getRating())
                        .reviewCount(s.getReviewCount())
                        .matchedMenuNames(Collections.emptyList())
                        .build();
                allItems.add(new ItemWrapper(score, 80, "SHOP", s.getId(), item));
            }
        }

        // 3. MENU
        if (searchAll || "MENU".equalsIgnoreCase(type)) {
            List<Menu> menus = searchQueryRepository.searchMenus(keyword, FETCH_LIMIT);
            for (Menu m : menus) {
                double score = calculateScore(m.getName(), keyword);
                IntegratedMenuItem item = IntegratedMenuItem.builder()
                        .id(m.getId())
                        .menuId(m.getId())
                        .name(m.getName())
                        .shopId(m.getShopId())
                        .shopName(null) // 연관관계 제거됨 (MVP)
                        .placeId(null)
                        .placeName(null)
                        .price(m.getPrice() != null ? m.getPrice().intValue() : 0)
                        .description(m.getDescription())
                        .thumbnailUrl(m.getImageUrl())
                        .build();
                allItems.add(new ItemWrapper(score, 60, "MENU", m.getId(), item));
            }
        }

        // 4. FESTIVAL
        if (searchAll || "FESTIVAL".equalsIgnoreCase(type)) {
            List<Festival> festivals = searchQueryRepository.searchFestivals(keyword, FETCH_LIMIT);
            for (Festival f : festivals) {
                double score = calculateScore(f.getName(), keyword);
                IntegratedFestivalItem item = IntegratedFestivalItem.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .region(f.getRegion())
                        .startDate(f.getStartDate())
                        .endDate(f.getEndDate())
                        .address(f.getLocation())
                        .thumbnailUrl(f.getThumbnailUrl())
                        .content(f.getDescription())
                        .build();
                allItems.add(new ItemWrapper(score, 40, "FESTIVAL", f.getId(), item));
            }
        }

        // 정렬
        allItems.sort((a, b) -> {
            if (Double.compare(b.score, a.score) != 0) return Double.compare(b.score, a.score);
            if (b.priority != a.priority) return Integer.compare(b.priority, a.priority);
            int typeCmp = a.targetType.compareTo(b.targetType);
            if (typeCmp != 0) return typeCmp;
            return Long.compare(b.id, a.id);
        });

        // 커서 필터링
        List<ItemWrapper> filtered = allItems;
        if (cursor != null) {
            filtered = allItems.stream()
                    .filter(w -> isAfterCursor(w, cursor))
                    .collect(Collectors.toList());
        }

        // 제한 (Limit)
        boolean hasNext = filtered.size() > size;
        List<ItemWrapper> paged = filtered.stream().limit(size).toList();

        String nextCursor = null;
        if (hasNext && !paged.isEmpty()) {
            ItemWrapper last = paged.get(paged.size() - 1);
            nextCursor = new IntegratedSearchCursor(last.score, last.priority, last.targetType, last.id).encode();
        }

        List<IntegratedSearchItem> items = paged.stream().map(w -> w.item).toList();
        return new CursorPageResponse<>(items, nextCursor, hasNext, size);
    }

    private double calculateScore(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) return 0.0;
        return text.equalsIgnoreCase(keyword) ? 100.0 : 50.0;
    }

    private boolean isAfterCursor(ItemWrapper w, IntegratedSearchCursor cursor) {
        if (Double.compare(cursor.relevanceScore(), w.score) != 0) return w.score < cursor.relevanceScore();
        if (cursor.priority() != w.priority) return w.priority < cursor.priority();
        int typeCmp = w.targetType.compareTo(cursor.targetType());
        if (typeCmp != 0) return typeCmp > 0;
        return w.id < cursor.id();
    }

    private record ItemWrapper(
            double score,
            int priority,
            String targetType,
            Long id,
            IntegratedSearchItem item
    ) {}
}
