package com.chunbaetour.domain.admin.shop.service;

import com.chunbaetour.domain.admin.shop.dto.request.AdminShopUpdateRequest;
import com.chunbaetour.domain.admin.shop.dto.response.AdminShopDetailResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** 가게 단건 상세 — 기본 정보 + 인증 상태 + 리뷰 집계. 없으면 SHOP_NOT_FOUND(404). */
    public AdminShopDetailResponse getShop(Long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
        return AdminShopDetailResponse.from(shop);
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

        return AdminShopDetailResponse.from(shop);
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
