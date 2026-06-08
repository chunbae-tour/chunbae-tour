package com.chunbaetour.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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
import com.chunbaetour.domain.store.repository.UserItemRepository;
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
        given(tokenIssuer.issueItemQr(USER_ID, ITEM_ID)).willReturn("qr-token");
        given(tokenIssuer.verifyItemQr("qr-token")).willReturn(new ItemQrClaims(USER_ID, ITEM_ID, expiresAt));

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
        stubClock();

        UserItemUseResponse response = userItemService.useByQr(VERIFIER_ID, SHOP_ID, "qr-token");

        assertThat(response.status()).isEqualTo(UserItemStatus.USED);
        assertThat(response.usedShopId()).isEqualTo(SHOP_ID);
        assertThat(response.usedAt()).isNotNull();
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
        Product product = Product.create("테스트 아이템", "설명", "쿠폰", 1000L,
                null, 10, "[]", "테스트 상점", 30, 1);
        ReflectionTestUtils.setField(product, "id", 100L);
        UserItem item = UserItem.create(userId, 200L, product, LocalDate.of(2026, 6, 8));
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        ReflectionTestUtils.setField(item, "status", status);
        return item;
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
