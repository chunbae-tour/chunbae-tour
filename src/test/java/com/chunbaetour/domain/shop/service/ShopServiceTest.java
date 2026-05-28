package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.ShopUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.QrCodeResponse;
import com.chunbaetour.domain.shop.dto.response.ShopInfoResponse;
import com.chunbaetour.domain.shop.dto.response.ShopResponse;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.MenuRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ShopService shopService;

    private static final Long USER_ID = 1L;
    private static final Long SHOP_ID = 10L;

    /** 실제 Shop 인스턴스 생성 — 빌더 기본값: status=ACTIVE */
    private Shop createShop() {
        return Shop.builder()
                .userId(USER_ID)
                .applicationId(1L)
                .shopName("광화문 떡볶이")
                .category("FOOD")
                .address("서울 종로구 세종대로 172")
                .phone("02-1234-5678")
                .description("전통 떡볶이 전문점")
                .build();
    }

    // ── GET /merchants/me/shops ────────────────────────────────────────────

    @Test
    @DisplayName("내 가게 목록 조회 — 성공")
    void getMyShops_success() {
        Shop shop = createShop();
        given(shopRepository.findAllByUserId(USER_ID)).willReturn(List.of(shop));

        List<ShopResponse> responses = shopService.getMyShops(USER_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).shopName()).isEqualTo("광화문 떡볶이");
    }

    // ── GET /merchants/me/shops/{shopId} ───────────────────────────────────

    @Test
    @DisplayName("내 가게 단건 조회 — 성공")
    void getMyShop_success() {
        Shop shop = createShop();
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));

        ShopResponse response = shopService.getMyShop(USER_ID, SHOP_ID);

        assertThat(response.shopName()).isEqualTo("광화문 떡볶이");
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(ShopStatus.ACTIVE);
    }

    @Test
    @DisplayName("내 가게 단건 조회 — 가게 없음 → SHOP_NOT_FOUND")
    void getMyShop_notFound_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.getMyShop(USER_ID, SHOP_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    // ── PATCH /merchants/me/shops/{shopId} ─────────────────────────────────

    @Test
    @DisplayName("내 가게 수정 — 성공 (부분 수정, 실제 값 변경 검증)")
    void updateMyShop_success() {
        Shop shop = createShop();
        ShopUpdateRequest request = new ShopUpdateRequest(
                "새로운 가게명", null, "02-9999-8888", "업데이트된 소개글", null, null, null
        );
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));

        ShopResponse response = shopService.updateMyShop(USER_ID, SHOP_ID, request);

        assertThat(response.shopName()).isEqualTo("새로운 가게명");
        assertThat(response.phone()).isEqualTo("02-9999-8888");
        assertThat(response.description()).isEqualTo("업데이트된 소개글");
        assertThat(response.category()).isEqualTo("FOOD");
    }

    @Test
    @DisplayName("내 가게 수정 — SUSPENDED 상태 → SHOP_INACTIVE")
    void updateMyShop_suspended_throws() {
        Shop shop = mock(Shop.class);
        ShopUpdateRequest request = new ShopUpdateRequest("새이름", null, null, null, null, null, null);
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
        given(shop.getStatus()).willReturn(ShopStatus.SUSPENDED);

        assertThatThrownBy(() -> shopService.updateMyShop(USER_ID, SHOP_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("내 가게 수정 — CLOSED 상태 → SHOP_INACTIVE")
    void updateMyShop_closed_throws() {
        Shop shop = mock(Shop.class);
        ShopUpdateRequest request = new ShopUpdateRequest("새이름", null, null, null, null, null, null);
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));
        given(shop.getStatus()).willReturn(ShopStatus.CLOSED);

        assertThatThrownBy(() -> shopService.updateMyShop(USER_ID, SHOP_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("내 가게 수정 — 가게 없음 → SHOP_NOT_FOUND")
    void updateMyShop_notFound_throws() {
        ShopUpdateRequest request = new ShopUpdateRequest(null, null, null, null, null, null, null);
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.updateMyShop(USER_ID, SHOP_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    // ── GET /merchants/me/shops/{shopId}/qr ───────────────────────────────

    @Test
    @DisplayName("QR 코드 조회 — 성공: qrPayload 형식 검증")
    void getMyQrCode_success() {
        Shop shop = createShop();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));

        QrCodeResponse response = shopService.getMyQrCode(USER_ID, SHOP_ID);

        assertThat(response.shopId()).isEqualTo(SHOP_ID);
        assertThat(response.shopName()).isEqualTo("광화문 떡볶이");
        assertThat(response.qrPayload()).isEqualTo("YEOPJEON_PAY:SHOP:" + SHOP_ID);
    }

    @Test
    @DisplayName("QR 코드 조회 — 가게 없음 → SHOP_NOT_FOUND")
    void getMyQrCode_notFound_throws() {
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.getMyQrCode(USER_ID, SHOP_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("QR 코드 조회 — SUSPENDED 가게도 QR 정상 반환 (상태 가드 없음)")
    void getMyQrCode_suspendedShop_success() {
        Shop shop = mock(Shop.class);
        given(shop.getId()).willReturn(SHOP_ID);
        given(shop.getShopName()).willReturn("광화문 떡볶이");
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));

        QrCodeResponse response = shopService.getMyQrCode(USER_ID, SHOP_ID);

        assertThat(response.qrPayload()).isEqualTo("YEOPJEON_PAY:SHOP:" + SHOP_ID);
    }

    // ── GET /shops/{shopId}/qr-info ────────────────────────────────────────

    @Test
    @DisplayName("QR 스캔 가게 정보 조회 — 성공: 메뉴 목록 포함")
    void getShopInfo_success() {
        // given
        Shop shop = createShop();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        Menu menu = Menu.builder().shopId(SHOP_ID).name("떡볶이").price(5000L).build();
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findByShopIdOrderByIdAsc(SHOP_ID)).willReturn(List.of(menu));

        // when
        ShopInfoResponse response = shopService.getShopInfo(SHOP_ID);

        // then
        assertThat(response.shopId()).isEqualTo(SHOP_ID);
        assertThat(response.shopName()).isEqualTo("광화문 떡볶이");
        assertThat(response.status()).isEqualTo(ShopStatus.ACTIVE);
        assertThat(response.menus()).hasSize(1);
        assertThat(response.menus().get(0).name()).isEqualTo("떡볶이");
    }

    @Test
    @DisplayName("QR 스캔 가게 정보 조회 — 가게 없음 → SHOP_NOT_FOUND")
    void getShopInfo_notFound_throws() {
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.getShopInfo(SHOP_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
        verify(menuRepository, never()).findByShopIdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("QR 스캔 가게 정보 조회 — 메뉴 없는 가게도 빈 목록으로 성공")
    void getShopInfo_noMenus_success() {
        Shop shop = createShop();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findByShopIdOrderByIdAsc(SHOP_ID)).willReturn(List.of());

        ShopInfoResponse response = shopService.getShopInfo(SHOP_ID);

        assertThat(response.menus()).isEmpty();
    }

    @Test
    @DisplayName("가게 공개 정보 조회 — isAvailable=false 메뉴도 응답에 포함")
    void getShopInfo_includesUnavailableMenus() {
        // given — 품절 메뉴(isAvailable=false)도 응답에 포함 — 프론트에서 비활성 표시 처리
        Shop shop = createShop();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        Menu availableMenu = Menu.builder().shopId(SHOP_ID).name("떡볶이").price(5000L).build();
        Menu unavailableMenu = mock(Menu.class);
        given(unavailableMenu.getId()).willReturn(2L);
        given(unavailableMenu.getShopId()).willReturn(SHOP_ID);
        given(unavailableMenu.getName()).willReturn("순대");
        given(unavailableMenu.getDescription()).willReturn(null);
        given(unavailableMenu.getPrice()).willReturn(4000L);
        given(unavailableMenu.getImageUrl()).willReturn(null);
        given(unavailableMenu.isAvailable()).willReturn(false);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findByShopIdOrderByIdAsc(SHOP_ID)).willReturn(List.of(availableMenu, unavailableMenu));

        ShopInfoResponse response = shopService.getShopInfo(SHOP_ID);

        assertThat(response.menus()).hasSize(2);
        assertThat(response.menus()).anyMatch(m -> m.name().equals("순대") && !m.isAvailable());
    }

    @Test
    @DisplayName("가게 공개 정보 조회 — SUSPENDED 상태 → SHOP_NOT_FOUND (공개 노출 차단)")
    void getShopInfo_suspended_throws() {
        // given — 관리자 신고 처리로 정지된 가게는 공개 조회 불가
        Shop shop = mock(Shop.class);
        given(shop.getStatus()).willReturn(ShopStatus.SUSPENDED);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        // then — 존재 여부 노출 방지를 위해 SHOP_NOT_FOUND로 통일
        assertThatThrownBy(() -> shopService.getShopInfo(SHOP_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
        verify(menuRepository, never()).findByShopIdOrderByIdAsc(any());
    }

    // ── updateShopStatus ──────────────────────────────────────────────────

    @Test
    @DisplayName("가게 상태 변경 — ACTIVE → SUSPENDED 성공")
    void updateShopStatus_activeToSuspended_success() {
        Shop shop = createShop(); // ACTIVE
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        shopService.updateShopStatus(SHOP_ID, ShopStatus.SUSPENDED);

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.SUSPENDED);
    }

    @Test
    @DisplayName("가게 상태 변경 — SUSPENDED → ACTIVE 성공 (복구)")
    void updateShopStatus_suspendedToActive_success() {
        Shop shop = mock(Shop.class);
        given(shop.getStatus()).willReturn(ShopStatus.SUSPENDED);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        shopService.updateShopStatus(SHOP_ID, ShopStatus.ACTIVE);

        verify(shop).updateStatus(ShopStatus.ACTIVE);
    }

    @Test
    @DisplayName("가게 상태 변경 — CLOSED 가게 → SHOP_INACTIVE")
    void updateShopStatus_closedShop_throws() {
        Shop shop = mock(Shop.class);
        given(shop.getStatus()).willReturn(ShopStatus.CLOSED);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        assertThatThrownBy(() -> shopService.updateShopStatus(SHOP_ID, ShopStatus.ACTIVE))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("가게 상태 변경 — CLOSED로 변경 시도 → INVALID_INPUT_VALUE")
    void updateShopStatus_toClosed_throws() {
        assertThatThrownBy(() -> shopService.updateShopStatus(SHOP_ID, ShopStatus.CLOSED))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("가게 상태 변경 — 가게 없음 → SHOP_NOT_FOUND")
    void updateShopStatus_notFound_throws() {
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.updateShopStatus(SHOP_ID, ShopStatus.SUSPENDED))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    // ── hideShop ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("가게 정지 — 성공: status SUSPENDED로 변경")
    void hideShop_success() {
        // given — ACTIVE 가게 정상 정지
        Shop shop = createShop();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        // when
        shopService.hideShop(SHOP_ID);

        // then
        assertThat(shop.getStatus()).isEqualTo(ShopStatus.SUSPENDED);
    }

    @Test
    @DisplayName("가게 숨김 — 가게 없음 → SHOP_NOT_FOUND")
    void hideShop_notFound_throws() {
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.hideShop(SHOP_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("가게 숨김 — CLOSED 상태 → SHOP_INACTIVE (폐업 가게 숨김 불가)")
    void hideShop_closedShop_throws() {
        // Shop.hide()가 CLOSED 상태에서 BusinessException(SHOP_INACTIVE)를 직접 던짐
        Shop shop = mock(Shop.class);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        doThrow(new BusinessException(ErrorCode.SHOP_INACTIVE))
                .when(shop).hide();

        assertThatThrownBy(() -> shopService.hideShop(SHOP_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    // ── findMerchantAccountId ──────────────────────────────────────────────

    @Test
    @DisplayName("상인 accountId 조회 — 성공: userId 반환")
    void findMerchantAccountId_success() {
        // given — createShop()은 userId = USER_ID로 생성
        Shop shop = createShop();
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        Optional<Long> result = shopService.findMerchantAccountId(SHOP_ID);

        assertThat(result).contains(USER_ID);
    }

    @Test
    @DisplayName("상인 accountId 조회 — 가게 없음 → Optional.empty()")
    void findMerchantAccountId_notFound_returnsEmpty() {
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.empty());

        Optional<Long> result = shopService.findMerchantAccountId(SHOP_ID);

        assertThat(result).isEmpty();
    }
}
