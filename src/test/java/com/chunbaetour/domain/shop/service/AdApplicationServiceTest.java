package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.AdApplicationRequest;
import com.chunbaetour.domain.shop.dto.response.AdApplicationResponse;
import com.chunbaetour.domain.shop.entity.AdApplication;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.AdApplicationRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.AdApplicationStatus;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdApplicationServiceTest {

    @Mock private AdApplicationRepository adApplicationRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private WalletService walletService;
    @Mock private RedissonClient redissonClient;

    @InjectMocks
    private AdApplicationService adApplicationService;

    private static final Long USER_ID = 1L;
    private static final Long SHOP_ID = 10L;
    private static final Long AD_ID = 1L;
    private static final LocalDate START = LocalDate.now().plusDays(3);
    private static final LocalDate END = LocalDate.now().plusDays(32);

    private Shop createShop() {
        Shop shop = mock(Shop.class);
        given(shop.getId()).willReturn(SHOP_ID);
        return shop;
    }

    private AdApplicationRequest validRequest() {
        return new AdApplicationRequest(SHOP_ID, "BANNER", START, END, 10_000L);
    }

    private AdApplication createAdApplication(Long id) {
        AdApplication a = AdApplication.create(SHOP_ID, "BANNER", START, END, 10_000L);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Test
    @DisplayName("광고 신청 성공 — AdApplication 저장 및 응답 반환")
    void applyAd_success() {
        Shop shop = createShop();
        AdApplication saved = createAdApplication(1L);

        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
        given(shopRepository.findByIdWithLock(SHOP_ID)).willReturn(Optional.of(shop));
        given(adApplicationRepository.findByShopIdAndStatusWithLock(SHOP_ID, AdApplicationStatus.PENDING))
                .willReturn(Collections.emptyList());
        given(adApplicationRepository.save(any(AdApplication.class))).willReturn(saved);

        AdApplicationResponse response = adApplicationService.applyAd(USER_ID, validRequest());

        assertThat(response.applicationId()).isEqualTo(1L);
        assertThat(response.shopId()).isEqualTo(SHOP_ID);
        assertThat(response.adType()).isEqualTo("BANNER");
        assertThat(response.status()).isEqualTo(AdApplicationStatus.PENDING);
        then(adApplicationRepository).should().save(any(AdApplication.class));
    }

    @Test
    @DisplayName("가게 없음 — SHOP_NOT_FOUND")
    void applyAd_shopNotFound() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adApplicationService.applyAd(USER_ID, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);

        then(adApplicationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("중복 PENDING 신청 — DUPLICATE_AD_APPLICATION")
    void applyAd_duplicatePending() {
        Shop shop = createShop();
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
        given(shopRepository.findByIdWithLock(SHOP_ID)).willReturn(Optional.of(shop));
        given(adApplicationRepository.findByShopIdAndStatusWithLock(SHOP_ID, AdApplicationStatus.PENDING))
                .willReturn(List.of(mock(AdApplication.class)));

        assertThatThrownBy(() -> adApplicationService.applyAd(USER_ID, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_AD_APPLICATION);

        then(adApplicationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦음 — INVALID_INPUT_VALUE")
    void applyAd_invalidDateRange() {
        Shop shop = createShop();
        AdApplicationRequest invalidRequest = new AdApplicationRequest(SHOP_ID, "BANNER", END, START, 10_000L);

        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));

        assertThatThrownBy(() -> adApplicationService.applyAd(USER_ID, invalidRequest))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        then(adApplicationRepository).should(never()).save(any());
    }

    // ===== extendAd =====

    private RLock mockLock() throws InterruptedException {
        RLock lock = mock(RLock.class);
        given(redissonClient.getLock(any(String.class))).willReturn(lock);
        given(lock.tryLock(3, 5, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        return lock;
    }

    private AdApplication createApproved(Long id) {
        AdApplication a = AdApplication.create(SHOP_ID, "BANNER", START, END, 30_000L); // 30일, 30000엽전 → 1000/일
        a.approve();
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Test
    @DisplayName("광고 연장 성공 — endDate 연장 및 엽전 차감 호출")
    void extendAd_success() throws InterruptedException {
        mockLock();
        Shop shop = createShop();
        AdApplication approved = createApproved(AD_ID);

        given(adApplicationRepository.findById(AD_ID)).willReturn(Optional.of(approved));
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
        given(adApplicationRepository.findByIdWithLock(AD_ID)).willReturn(Optional.of(approved));

        LocalDate originalEnd = approved.getEndDate();
        AdApplicationResponse response = adApplicationService.extendAd(USER_ID, AD_ID, 7);

        assertThat(response.endDate()).isEqualTo(originalEnd.plusDays(7));
        then(walletService).should().spendForAdExtension(USER_ID, 7_000L, "BANNER"); // 1000/일 × 7일
    }

    @Test
    @DisplayName("광고 연장 — 광고 미존재 AD_APPLICATION_NOT_FOUND")
    void extendAd_notFound() throws InterruptedException {
        mockLock();
        given(adApplicationRepository.findById(AD_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adApplicationService.extendAd(USER_ID, AD_ID, 7))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AD_APPLICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("광고 연장 — 본인 가게 아님 SHOP_NOT_FOUND")
    void extendAd_shopNotOwned() throws InterruptedException {
        mockLock();
        AdApplication approved = createApproved(AD_ID);
        given(adApplicationRepository.findById(AD_ID)).willReturn(Optional.of(approved));
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adApplicationService.extendAd(USER_ID, AD_ID, 7))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("광고 연장 — APPROVED 아닌 상태 AD_APPLICATION_INVALID_STATUS")
    void extendAd_invalidStatus() throws InterruptedException {
        mockLock();
        Shop shop = createShop();
        AdApplication pending = createAdApplication(AD_ID); // PENDING 상태

        given(adApplicationRepository.findById(AD_ID)).willReturn(Optional.of(pending));
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
        given(adApplicationRepository.findByIdWithLock(AD_ID)).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> adApplicationService.extendAd(USER_ID, AD_ID, 7))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AD_APPLICATION_INVALID_STATUS);
    }

}
