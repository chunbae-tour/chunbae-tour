package com.chunbaetour.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.auth.jwt.IssuedItemQr;
import com.chunbaetour.domain.auth.jwt.ItemQrClaims;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.chunbaetour.domain.store.dto.response.UserItemQrResponse;
import com.chunbaetour.domain.store.dto.response.UserItemUseResponse;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.entity.UserItem;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.repository.UserItemRepository;
import com.chunbaetour.domain.store.type.RedemptionScope;
import com.chunbaetour.domain.store.type.UserItemStatus;
import io.jsonwebtoken.ExpiredJwtException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserItemQrServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long VERIFIER_ID = 2L;
    private static final Long ITEM_ID = 10L;
    private static final Long SHOP_ID = 20L;
    private static final Instant NOW = Instant.parse("2026-06-08T00:00:00Z");

    @Mock private UserItemRepository userItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private TokenIssuer tokenIssuer;
    @Mock private Clock clock;
    @InjectMocks private UserItemService userItemService;

    @Test
    @DisplayName("보유 아이템 QR 발급은 본인 AVAILABLE 아이템이면 5분 토큰을 반환한다")
    void issueQr_success() {
        UserItem item = item(USER_ID, UserItemStatus.AVAILABLE);
        Instant expiresAt = NOW.plusSeconds(300);
        stubClock();
        given(userItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        // 발급 직후 재파싱 없이 issueItemQr이 토큰+만료시각을 함께 반환
        given(tokenIssuer.issueItemQr(USER_ID, ITEM_ID)).willReturn(new IssuedItemQr("qr-token", expiresAt));

        UserItemQrResponse response = userItemService.issueQr(USER_ID, ITEM_ID);

        assertThat(response.token()).isEqualTo("qr-token");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("타인 아이템 QR 발급은 ITEM_FORBIDDEN 예외가 발생한다")
    void issueQr_forbidden() {
        given(userItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item(99L, UserItemStatus.AVAILABLE)));

        assertThatThrownBy(() -> userItemService.issueQr(USER_ID, ITEM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_FORBIDDEN);
    }

    @Test
    @DisplayName("이미 사용된 아이템 QR 발급은 ITEM_ALREADY_USED 예외가 발생한다")
    void issueQr_alreadyUsed() {
        given(userItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item(USER_ID, UserItemStatus.USED)));

        assertThatThrownBy(() -> userItemService.issueQr(USER_ID, ITEM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_ALREADY_USED);
    }

    @Test
    @DisplayName("검증자가 사용자 아이템 QR을 확인하면 아이템을 USED 처리한다")
    void useByQr_success() {
        Shop shop = shop();
        UserItem item = item(USER_ID, UserItemStatus.AVAILABLE);
        given(shopRepository.findByIdAndUserId(SHOP_ID, VERIFIER_ID)).willReturn(Optional.of(shop));
        given(tokenIssuer.verifyItemQr("qr-token")).willReturn(new ItemQrClaims(USER_ID, ITEM_ID, NOW.plusSeconds(300)));
        given(userItemRepository.findByIdWithLock(ITEM_ID)).willReturn(Optional.of(item));
        given(productRepository.findById(100L)).willReturn(Optional.of(product(RedemptionScope.GLOBAL)));
        stubClock();

        UserItemUseResponse response = userItemService.useByQr(VERIFIER_ID, SHOP_ID, "qr-token");

        assertThat(response.status()).isEqualTo(UserItemStatus.USED);
        assertThat(response.usedShopId()).isEqualTo(SHOP_ID);
        assertThat(response.usedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 USED된 아이템에 같은 토큰으로 재사용을 시도하면 ITEM_ALREADY_USED로 멱등 차단된다")
    void useByQr_alreadyUsed_idempotent() {
        // 첫 사용으로 USED 전환된 아이템을 두 번째 useByQr이 findByIdWithLock으로 다시 잡은 상황 재현.
        // 비관락 + USED 상태 검증으로 이중 사용(double-redeem)을 막는다.
        given(shopRepository.findByIdAndUserId(SHOP_ID, VERIFIER_ID)).willReturn(Optional.of(shop()));
        given(tokenIssuer.verifyItemQr("qr-token")).willReturn(new ItemQrClaims(USER_ID, ITEM_ID, NOW.plusSeconds(300)));
        given(userItemRepository.findByIdWithLock(ITEM_ID)).willReturn(Optional.of(item(USER_ID, UserItemStatus.USED)));

        assertThatThrownBy(() -> userItemService.useByQr(VERIFIER_ID, SHOP_ID, "qr-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_ALREADY_USED);
    }

    @Test
    @DisplayName("만료일이 어제인 아이템은 ITEM_EXPIRED로 사용 거부된다")
    void useByQr_expiredByDate() {
        // expiresAt=어제(6/7) < 오늘(6/8) → 만료 처리
        given(shopRepository.findByIdAndUserId(SHOP_ID, VERIFIER_ID)).willReturn(Optional.of(shop()));
        given(tokenIssuer.verifyItemQr("qr-token")).willReturn(new ItemQrClaims(USER_ID, ITEM_ID, NOW.plusSeconds(300)));
        given(userItemRepository.findByIdWithLock(ITEM_ID))
                .willReturn(Optional.of(item(USER_ID, UserItemStatus.AVAILABLE, LocalDate.of(2026, 6, 7))));
        stubClock();

        assertThatThrownBy(() -> userItemService.useByQr(VERIFIER_ID, SHOP_ID, "qr-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_EXPIRED);
    }

    @Test
    @DisplayName("만료일이 '오늘'인 아이템은 당일까지 사용 가능하다(inclusive-through-date 경계)")
    void useByQr_expiresToday_stillUsable() {
        // expiresAt=오늘(6/8) — isBefore(오늘)=false → 만료 아님, 정상 USED 처리
        given(shopRepository.findByIdAndUserId(SHOP_ID, VERIFIER_ID)).willReturn(Optional.of(shop()));
        given(tokenIssuer.verifyItemQr("qr-token")).willReturn(new ItemQrClaims(USER_ID, ITEM_ID, NOW.plusSeconds(300)));
        given(userItemRepository.findByIdWithLock(ITEM_ID))
                .willReturn(Optional.of(item(USER_ID, UserItemStatus.AVAILABLE, LocalDate.of(2026, 6, 8))));
        given(productRepository.findById(100L)).willReturn(Optional.of(product(RedemptionScope.GLOBAL)));
        stubClock();

        UserItemUseResponse response = userItemService.useByQr(VERIFIER_ID, SHOP_ID, "qr-token");

        assertThat(response.status()).isEqualTo(UserItemStatus.USED);
    }

    @Test
    @DisplayName("GLOBAL이 아닌 사용처 한정(SHOP_ONLY) 상품은 ITEM_FORBIDDEN으로 사용 거부된다 (향후 scope 검증)")
    void useByQr_nonGlobalScope_forbidden() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, VERIFIER_ID)).willReturn(Optional.of(shop()));
        given(tokenIssuer.verifyItemQr("qr-token")).willReturn(new ItemQrClaims(USER_ID, ITEM_ID, NOW.plusSeconds(300)));
        given(userItemRepository.findByIdWithLock(ITEM_ID)).willReturn(Optional.of(item(USER_ID, UserItemStatus.AVAILABLE)));
        given(productRepository.findById(100L)).willReturn(Optional.of(product(RedemptionScope.SHOP_ONLY)));
        stubClock();

        assertThatThrownBy(() -> userItemService.useByQr(VERIFIER_ID, SHOP_ID, "qr-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_FORBIDDEN);
    }

    @Test
    @DisplayName("만료된 사용자 아이템 QR은 ITEM_QR_EXPIRED 예외가 발생한다")
    void useByQr_expiredToken() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, VERIFIER_ID)).willReturn(Optional.of(shop()));
        given(tokenIssuer.verifyItemQr("qr-token"))
                .willThrow(new ExpiredJwtException(null, null, "expired"));

        assertThatThrownBy(() -> userItemService.useByQr(VERIFIER_ID, SHOP_ID, "qr-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_QR_EXPIRED);
    }

    private UserItem item(Long userId, UserItemStatus status) {
        return item(userId, status, LocalDate.of(2026, 6, 8));
    }

    private UserItem item(Long userId, UserItemStatus status, LocalDate expiresAt) {
        Product product = Product.create("테스트 아이템", "설명", "쿠폰", 1000L,
                null, 10, "[]", "테스트 상점", 30, 1);
        ReflectionTestUtils.setField(product, "id", 100L);
        // create(...)의 4번째 인자는 구매일(today)이며 expiresAt=today+validityDays로 계산된다.
        // 경계 테스트는 정확한 expiresAt이 필요하므로 생성 후 필드를 직접 덮어쓴다.
        UserItem item = UserItem.create(userId, 200L, product, LocalDate.of(2026, 6, 8));
        ReflectionTestUtils.setField(item, "expiresAt", expiresAt);
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        ReflectionTestUtils.setField(item, "status", status);
        return item;
    }

    private Product product(RedemptionScope scope) {
        Product product = Product.create("테스트 아이템", "설명", "쿠폰", 1000L,
                null, 10, "[]", "테스트 상점", 30, 1);
        ReflectionTestUtils.setField(product, "id", 100L);
        ReflectionTestUtils.setField(product, "redemptionScope", scope);
        return product;
    }

    private Shop shop() {
        Shop shop = Shop.builder()
                .userId(VERIFIER_ID)
                .applicationId(300L)
                .shopName("테스트 가게")
                .category("음식")
                .address("서울")
                .build();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        ReflectionTestUtils.setField(shop, "status", ShopStatus.ACTIVE);
        return shop;
    }

    private void stubClock() {
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
    }
}
