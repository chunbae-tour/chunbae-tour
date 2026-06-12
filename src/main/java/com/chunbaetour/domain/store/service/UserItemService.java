package com.chunbaetour.domain.store.service;

import com.chunbaetour.domain.auth.jwt.IssuedItemQr;
import com.chunbaetour.domain.auth.jwt.ItemQrClaims;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.chunbaetour.domain.store.dto.response.UserItemQrResponse;
import com.chunbaetour.domain.store.dto.response.UserItemResponse;
import com.chunbaetour.domain.store.dto.response.UserItemUseResponse;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.entity.UserItem;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.repository.UserItemRepository;
import com.chunbaetour.domain.store.type.RedemptionScope;
import com.chunbaetour.domain.store.type.UserItemStatus;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 보유 아이템 서비스.
 * 담당 기능: cursor 페이징 기반 보유 아이템 조회, 사용자 아이템 QR 발급, 아이템 사용 처리.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserItemService {

    private final UserItemRepository userItemRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    /** 내 보유 아이템 조회 — cursor keyset 페이징 (id DESC) */
    public CursorPageResponse<UserItemResponse> getMyItems(Long userId, String cursor, int size) {
        if (size <= 0) {
            return new CursorPageResponse<>(List.of(), null, false, 0);
        }
        // cursor 디코딩 — null이면 첫 페이지
        Long cursorId = CursorUtils.decodeSafe(cursor);

        // size+1 조회로 다음 페이지 존재 여부 판별
        List<UserItem> items = userItemRepository.findItemsByUserIdWithCursor(
                userId, cursorId, PageRequest.of(0, size + 1));

        // 다음 페이지 판별·매핑·커서 인코딩을 공통 팩토리로 위임 (KAN-295)
        return CursorPageResponse.of(items, size, UserItemResponse::from, UserItem::getId);
    }

    public UserItemQrResponse issueQr(Long userId, Long itemId) {
        UserItem item = userItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        validateOwner(item, userId);
        validateAvailable(item);

        // 발급 토큰과 만료시각을 함께 받는다 — 발급 직후 재파싱(verifyItemQr) 없이 그대로 응답.
        IssuedItemQr issued = tokenIssuer.issueItemQr(userId, itemId);
        return new UserItemQrResponse(issued.token(), issued.expiresAt());
    }

    @Transactional
    public UserItemUseResponse useByQr(Long verifierUserId, Long shopId, String token) {
        Shop shop = shopRepository.findByIdAndUserId(shopId, verifierUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }

        ItemQrClaims claims = verifyItemQr(token);
        UserItem item = userItemRepository.findByIdWithLock(claims.itemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        // QR claim의 userId는 위변조 교차검증용 — itemId만으로 조회한 아이템의 실제 소유자와 토큰 발급 대상이
        // 일치하는지 확인한다. 불일치면 토큰이 변조됐거나 다른 아이템에 재사용된 것이므로 무효 처리.
        if (!item.getUserId().equals(claims.userId())) {
            throw new BusinessException(ErrorCode.ITEM_QR_INVALID);
        }
        validateAvailable(item);

        // 사용처(scope) 검증 — 아이템 상품이 이 검증자/가게에서 사용 가능한지 확인
        Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        validateRedeemableAt(product, shop);

        item.use(LocalDateTime.now(clock), shop.getId());
        return UserItemUseResponse.from(item);
    }

    /**
     * 사용처(redemption scope) 검증. 현재 스토어 상품은 전부 {@link RedemptionScope#GLOBAL}(전 가맹점 공통)이라
     * ACTIVE 검증자면 통과한다.
     *
     * <p>TODO(redemption 도메인 확장, KAN-??-item-redemption-mode): 가게/파트너/행사/장소 한정 상품이 도입되면
     * scope별 검증을 채운다 — 예: SHOP_ONLY는 product의 소속 shop == 스캔한 shop 일치 확인,
     * PARTNER_ONLY/EVENT_ONLY/PLACE_ONLY는 item_redemption_permissions 매핑 조회. (hanes/미구현API.md 참조)
     * 미충족 시 {@link ErrorCode#ITEM_FORBIDDEN}.
     */
    private void validateRedeemableAt(Product product, Shop shop) {
        switch (product.getRedemptionScope()) {
            case GLOBAL -> { /* 전 가맹점 공통 — 어디서나 사용 가능 */ }
            // TODO: 아래 scope들은 redemption 도메인 확장에서 구현. 현재는 도달 불가(모든 상품 GLOBAL).
            case SHOP_ONLY, PARTNER_ONLY, EVENT_ONLY, PLACE_ONLY ->
                    throw new BusinessException(ErrorCode.ITEM_FORBIDDEN);
        }
    }

    private ItemQrClaims verifyItemQr(String token) {
        try {
            return tokenIssuer.verifyItemQr(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.ITEM_QR_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.ITEM_QR_INVALID);
        }
    }

    private void validateOwner(UserItem item, Long userId) {
        if (!item.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ITEM_FORBIDDEN);
        }
    }

    private void validateAvailable(UserItem item) {
        if (item.getStatus() == UserItemStatus.USED) {
            throw new BusinessException(ErrorCode.ITEM_ALREADY_USED);
        }
        // 만료 판단의 Source of Truth는 expiresAt(날짜)다. EXPIRED 상태값은 현재 프로덕션에서 쓰지 않으며
        // (만료 전이 배치 미도입), 향후 배치로 EXPIRED를 미리 박아 빠른 필터링하려는 denormalization용 방어 분기다.
        // 따라서 두 검사는 이원화가 아니라 "날짜=권위, 상태=옵션 캐시" 관계 — 배치 도입 전엔 expiresAt이 항상 최종 판단.
        if (item.getStatus() == UserItemStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.ITEM_EXPIRED);
        }
        // 만료일은 inclusive-through-date — 만료일 '당일'까지는 사용 가능(쿠폰/교환권 관례).
        // 예: expiresAt=6/9면 6/9 23:59까지 OK, 6/10부터 만료. 그래서 isBefore(오늘)만 만료 처리.
        if (item.getExpiresAt() != null && item.getExpiresAt().isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.ITEM_EXPIRED);
        }
        if (item.getStatus() != UserItemStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
