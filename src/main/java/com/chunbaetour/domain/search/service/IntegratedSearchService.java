package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.search.dto.request.IntegratedSearchCursor;
import com.chunbaetour.domain.search.dto.response.integrated.*;
import com.chunbaetour.domain.search.repository.SearchQueryRepository;
import com.chunbaetour.domain.search.type.SearchSource;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntegratedSearchService {

    private final SearchQueryRepository searchQueryRepository;
    private final PopularSearchService popularSearchService;

    public CursorPageResponse<IntegratedSearchItem> searchIntegrated(String keyword, String type, String cursorStr, int size, String clientIp, boolean track, String source) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        keyword = keyword.trim();
        SearchSource searchSource = SearchSource.from(source);
        if (keyword.length() > 50) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        boolean searchAll = "ALL".equalsIgnoreCase(type);
        if (!searchAll && !List.of("PLACE", "SHOP", "MENU", "FESTIVAL", "TRADITIONAL_MARKET").contains(type.toUpperCase(java.util.Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (cursorStr != null && cursorStr.length() > 1000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        IntegratedSearchCursor cursor = IntegratedSearchCursor.decode(cursorStr);
        List<ItemWrapper> allItems = new ArrayList<>();
        
        // 1. PLACE
        if (searchAll || "PLACE".equalsIgnoreCase(type)) {
            List<Place> places = searchQueryRepository.searchPlaces(keyword);
            for (Place p : places) {
                double score = calculateScore(p.getName(), keyword);
                IntegratedPlaceItem item = IntegratedPlaceItem.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .category(p.getCategory() != null ? p.getCategory().name() : null)
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
            List<Shop> shops = searchQueryRepository.searchShops(keyword);
            for (Shop s : shops) {
                double score = calculateScore(s.getShopName(), keyword);
                IntegratedShopItem item = IntegratedShopItem.builder()
                        .id(s.getId())
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
            List<Menu> menus = searchQueryRepository.searchMenus(keyword);
            for (Menu m : menus) {
                double score = calculateScore(m.getName(), keyword);
                IntegratedMenuItem item = IntegratedMenuItem.builder()
                        .id(m.getId())
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
            List<Festival> festivals = searchQueryRepository.searchFestivals(keyword);
            for (Festival f : festivals) {
                double score = calculateScore(f.getName(), keyword);
                IntegratedFestivalItem item = IntegratedFestivalItem.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .region(f.getRegion())
                        .startDate(f.getStartDate())
                        .endDate(f.getEndDate())
                        .address(f.getAddress())
                        .imageUrl(f.getImageUrl())
                        .content(f.getDescription())
                        .build();
                allItems.add(new ItemWrapper(score, 40, "FESTIVAL", f.getId(), item));
            }
        }

        // 5. TRADITIONAL_MARKET
        if (searchAll || "TRADITIONAL_MARKET".equalsIgnoreCase(type)) {
            List<TraditionalMarket> markets = searchQueryRepository.searchTraditionalMarkets(keyword);
            for (TraditionalMarket m : markets) {
                double score = calculateScore(m.getName(), keyword);
                IntegratedTraditionalMarketItem item = IntegratedTraditionalMarketItem.builder()
                        .id(m.getId())
                        .name(m.getName())
                        .marketType(m.getMarketType())
                        .address(m.getAddress())
                        .phoneNumber(m.getPhoneNumber())
                        .homepageUrl(m.getHomepageUrl())
                        .establishYear(m.getEstablishYear())
                        .build();
                allItems.add(new ItemWrapper(score, 50, "TRADITIONAL_MARKET", m.getId(), item));
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
        // [주의] 본 인메모리 페이징 구조에서 커서는 클라이언트가 이미 본 항목을 건너뛰기 위한(Skip) 용도이지,
        // DB 단의 오프셋(Offset) 커서가 아닙니다. 
        // 2페이지, 3페이지 요청 시에도 DB에서는 항상 상위 200건씩만 가져와서 병합 후 건너뜁니다.
        // 이는 여러 도메인(Place, Shop, Menu, Festival)의 정렬 점수를 애플리케이션 메모리에서 통일하기 위한 MVP 구조적 한계이며,
        // 후속 개발 시 이 로직을 단순히 DB 커서로 착각하여 최적화하려다 정렬 버그를 유발하지 않도록 주의 바랍니다.
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
        // 첫 페이지에서 실제 결과가 있는 사용자 검색만 인기검색어로 집계한다.
        if (track && searchSource.isTrackable() && cursor == null && !items.isEmpty()) {
            popularSearchService.incrementSearchCount(keyword, clientIp);
        }
        // 복합 커서(score+priority+type+id)라 조립형 팩토리 — size echo 통일(items.size() 아님, KAN-325)
        return CursorPageResponse.ofAssembled(items, nextCursor, hasNext, size);
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
