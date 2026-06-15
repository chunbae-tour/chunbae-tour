package com.chunbaetour.domain.admin.shop.service;

import com.chunbaetour.domain.admin.shop.dto.request.AdminShopUpdateRequest;
import com.chunbaetour.domain.admin.shop.dto.response.AdminShopDetailResponse;
import com.chunbaetour.domain.admin.shop.dto.response.AdminShopListResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 운영자 가게 관리 서비스 (KAN-203, Admin Epic KAN-177 S04).
 *
 * <p>조회는 클래스 기본 {@code @Transactional(readOnly = true)}, 수정만 쓰기 {@code @Transactional}로 override —
 * S02 {@code AdminUserService} 패턴 일관. PATCH endpoint의 audit 기록은 컨트롤러의
 * {@link com.chunbaetour.domain.admin.audit.LogAdminAction} AOP가 담당하며 본 서비스는 도메인 상태 전이만 한다.
 *
 * <p>결정 B(SUSPENDED 재사용, HIDDEN 미도입): status 전이는 {@link Shop#hide()}(→SUSPENDED) /
 * {@link Shop#activate()}(→ACTIVE)로 처리. CLOSED 직접 지정은 거부.
 *
 * <p>{@link #getTotalShops()}/{@link #getSuspendedShops()}는 S06 대시보드 의존 — 가게 카운트 쿼리는
 * 본 서비스에만 두고 대시보드는 조합만 한다. 본 슬라이스에서는 메서드 노출까지만(wiring은 S06).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminShopService {

    private final ShopRepository shopRepository;
    private final PlaceRepository placeRepository;
    private final TraditionalMarketRepository traditionalMarketRepository;

    /**
     * 운영자 가게 목록 검색 (KAN-307) — keyword(가게명)·status 옵션 필터 + cursor 페이징.
     *
     * <p>keyword 공백은 null로 정규화(전체 조회), LIKE 와일드카드는 이스케이프(ESCAPE '\'). size+1 sentinel로
     * 다음 페이지 판단(AdminPlaceService.getPlaces 미러). 연결 장소/시장 이름은 페이지 단위 배치 조회(findAllById)로
     * 채워 N+1을 피한다.
     */
    public CursorPageResponse<AdminShopListResponse> getShops(
            String keyword, ShopStatus status, String cursor, int size) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? "%" + escapeLike(keyword.trim()) + "%" : null;
        Long cursorId = CursorUtils.decodeSafe(cursor);

        List<Shop> shops = shopRepository.searchForAdmin(
                normalizedKeyword, status, cursorId, PageRequest.of(0, size + 1));

        // 연결 장소/시장 이름 — 페이지(+sentinel) id를 모아 한 번에 배치 조회(N+1 회피). 미연결/미존재는 Map 누락 → null.
        // sentinel 1건이 섞여도 무방 — of()가 매핑을 trim된 content에만 적용한다.
        Map<Long, String> placeNames = fetchPlaceNames(shops);
        Map<Long, String> marketNames = fetchMarketNames(shops);

        // 공통 팩토리 of()로 위임 (KAN-295) — hasNext 판별·nextCursor 인코딩·size 요청값 echo·size<1 fail-fast 일관.
        return CursorPageResponse.of(shops, size,
                shop -> AdminShopListResponse.from(shop,
                        shop.getPlaceId() == null ? null : placeNames.get(shop.getPlaceId()),
                        shop.getTraditionalMarketId() == null ? null : marketNames.get(shop.getTraditionalMarketId())),
                Shop::getId);
    }

    /** 가게 단건 상세 — 기본 정보 + 인증 + 리뷰 집계 + 연결 장소/시장. 없으면 SHOP_NOT_FOUND(404). */
    public AdminShopDetailResponse getShop(Long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
        return buildDetail(shop);
    }

    /**
     * 가게 partial update — status/description/phone/operatingHours.
     *
     * <p>status 전이(결정 B): ACTIVE → {@link Shop#activate()}, SUSPENDED → {@link Shop#hide()}.
     * CLOSED 직접 지정은 폐업 전용이므로 INVALID_INPUT_VALUE(400)로 거부 — status 전이 후 나머지 필드를
     * {@link Shop#adminUpdate(String, String, String)}로 null-skip 반영한다(상인용 update()의 ACTIVE 가드 우회).
     */
    @Transactional
    public AdminShopDetailResponse updateShop(Long shopId, AdminShopUpdateRequest request) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        if (request.status() != null) {
            applyStatus(shop, request.status());
        }
        shop.adminUpdate(request.description(), request.phone(), request.operatingHours());

        return buildDetail(shop);
    }

    /** 단건 상세 — 연결 장소/시장 이름을 각각 단건 조회해 채운다(단건이라 N+1 무관). 미연결·미존재 시 null. */
    private AdminShopDetailResponse buildDetail(Shop shop) {
        String placeName = shop.getPlaceId() == null ? null
                : placeRepository.findById(shop.getPlaceId()).map(Place::getName).orElse(null);
        String marketName = shop.getTraditionalMarketId() == null ? null
                : traditionalMarketRepository.findById(shop.getTraditionalMarketId())
                        .map(TraditionalMarket::getName).orElse(null);
        return AdminShopDetailResponse.from(shop, placeName, marketName);
    }

    /** 페이지 내 연결 placeId들을 모아 Place 이름을 배치 조회(findAllById) → id→name Map. */
    private Map<Long, String> fetchPlaceNames(List<Shop> shops) {
        List<Long> ids = shops.stream()
                .map(Shop::getPlaceId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return placeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Place::getId, Place::getName));
    }

    /** 페이지 내 연결 traditionalMarketId들을 모아 TraditionalMarket 이름을 배치 조회 → id→name Map. */
    private Map<Long, String> fetchMarketNames(List<Shop> shops) {
        List<Long> ids = shops.stream()
                .map(Shop::getTraditionalMarketId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return traditionalMarketRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(TraditionalMarket::getId, TraditionalMarket::getName));
    }

    /**
     * LIKE 와일드카드 이스케이프 — {@code \ % _}를 리터럴로 처리(ESCAPE '\' 전제). 백슬래시를 먼저 치환해 이중 이스케이프 방지.
     * (AdminPlaceService.escapeLike와 동일 정책.)
     */
    private static String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /** status 요청 분기 — ACTIVE/SUSPENDED만 허용, CLOSED 직접 지정 거부. */
    private void applyStatus(Shop shop, ShopStatus status) {
        switch (status) {
            case ACTIVE -> shop.activate();
            case SUSPENDED -> shop.hide();
            // CLOSED(폐업)는 본 endpoint로 지정 불가 — 별도 절차.
            case CLOSED -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // ── S06 대시보드 의존 카운트 (본 슬라이스는 메서드 노출까지) ────────────────────

    /** 전체 가게 수. */
    public long getTotalShops() {
        return shopRepository.count();
    }

    /** SUSPENDED(노출 차단) 상태 가게 수. */
    public long getSuspendedShops() {
        return shopRepository.countByStatus(ShopStatus.SUSPENDED);
    }
}
