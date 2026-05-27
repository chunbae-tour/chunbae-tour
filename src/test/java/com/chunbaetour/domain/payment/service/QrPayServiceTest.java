package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.dto.request.QrPayCreateRequest;
import com.chunbaetour.domain.payment.dto.request.QrPayItemRequest;
import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse;
import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.repository.QrPayRequestRepository;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.MenuRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import org.springframework.dao.DataIntegrityViolationException;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QrPayServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private QrPayRequestRepository qrPayRequestRepository;

    @Mock
    private WalletRepository walletRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    // 고정 시각으로 Clock 주입 — expiredAt 범위 검증 가능
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-05-25T10:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private QrPayService qrPayService;

    private static final Long USER_ID = 1L;
    private static final Long MERCHANT_USER_ID = 99L; // 상인 ID — USER_ID(결제자)와 다른 계정
    private static final Long SHOP_ID = 10L;
    private static final Long MENU_ID_1 = 100L;
    private static final Long MENU_ID_2 = 101L;

    private Shop createActiveShop() {
        Shop shop = Shop.builder()
                .userId(MERCHANT_USER_ID)
                .applicationId(1L)
                .shopName("광화문 떡볶이")
                .category("FOOD")
                .address("서울 종로구")
                .phone("02-1234-5678")
                .description("전통 떡볶이 전문점")
                .build();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        return shop;
    }

    private Wallet createWallet(long balance) {
        Wallet wallet = mock(Wallet.class);
        given(wallet.getBalance()).willReturn(balance);
        return wallet;
    }

    private Menu createMenu(Long menuId, Long shopId, String name, Long price, boolean isAvailable) {
        Menu menu = mock(Menu.class);
        given(menu.getId()).willReturn(menuId);
        given(menu.getShopId()).willReturn(shopId);
        // 이하 3개는 검증 통과 후 성공 경로에서만 호출 — 실패 테스트에서 stub 미사용 시 예외 방지
        lenient().when(menu.getName()).thenReturn(name);
        lenient().when(menu.getPrice()).thenReturn(price);
        lenient().when(menu.isAvailable()).thenReturn(isAvailable);
        return menu;
    }

    // ── POST /payments/qr ─────────────────────────────────────────────────────

    @Test
    @DisplayName("QR 결제 요청 생성 — 성공: payRequestId 비어있지 않고 총금액 정확")
    void createQrPayRequest_success() {
        // given
        Shop shop = createActiveShop();
        Menu menu1 = createMenu(MENU_ID_1, SHOP_ID, "떡볶이", 5000L, true);
        Menu menu2 = createMenu(MENU_ID_2, SHOP_ID, "순대", 4000L, true);

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 2),
                new QrPayItemRequest(MENU_ID_2, 1)
        ));

        Wallet wallet = createWallet(50_000L);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findAllById(List.of(MENU_ID_1, MENU_ID_2))).willReturn(List.of(menu1, menu2));

        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));
        given(qrPayRequestRepository.saveAndFlush(any(QrPayRequest.class))).willAnswer(i -> i.getArgument(0));

        // when
        QrPayCreateResponse response = qrPayService.createQrPayRequest(USER_ID, request);

        // then — totalAmount = 5000*2 + 4000*1 = 14000
        assertThat(response.payRequestId()).isNotBlank();
        assertThat(response.totalAmount()).isEqualTo(14_000L);
        assertThat(response.shopId()).isEqualTo(SHOP_ID);
        assertThat(response.shopName()).isEqualTo("광화문 떡볶이");
        assertThat(response.menuItems()).hasSize(2);
        // Clock 고정(10:00:00) + 5분 = 10:05:00 정확히 검증
        LocalDateTime expectedExpiry = LocalDateTime.of(2026, 5, 25, 10, 5, 0);
        assertThat(response.expiredAt()).isEqualTo(expectedExpiry);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — 메뉴 스냅샷 내용 검증")
    void createQrPayRequest_snapshotContents() {
        // given
        Shop shop = createActiveShop();
        Menu menu = createMenu(MENU_ID_1, SHOP_ID, "떡볶이", 5000L, true);

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 3)
        ));

        Wallet wallet = createWallet(50_000L);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findAllById(List.of(MENU_ID_1))).willReturn(List.of(menu));

        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));
        given(qrPayRequestRepository.saveAndFlush(any(QrPayRequest.class))).willAnswer(i -> i.getArgument(0));

        // when
        QrPayCreateResponse response = qrPayService.createQrPayRequest(USER_ID, request);

        // then — 스냅샷에 결제 시점 메뉴 정보 포함
        assertThat(response.menuItems()).hasSize(1);
        assertThat(response.menuItems().get(0).menuId()).isEqualTo(MENU_ID_1);
        assertThat(response.menuItems().get(0).name()).isEqualTo("떡볶이");
        assertThat(response.menuItems().get(0).price()).isEqualTo(5000L);
        assertThat(response.menuItems().get(0).quantity()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualTo(15_000L); // 5000 * 3
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — 중복 menuId → INVALID_REQUEST")
    void createQrPayRequest_duplicateMenuId_throws() {
        // given — 같은 menuId(100)를 두 번 포함한 요청
        Shop shop = createActiveShop();
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 1),
                new QrPayItemRequest(MENU_ID_1, 2)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — 가게 없음 → SHOP_NOT_FOUND")
    void createQrPayRequest_shopNotFound_throws() {
        // given
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.empty());
        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 1)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — SUSPENDED 가게 → SHOP_INACTIVE")
    void createQrPayRequest_shopSuspended_throws() {
        // given — SUSPENDED 상태는 빌더로 생성 불가, mock 사용
        Shop shop = mock(Shop.class);
        given(shop.getStatus()).willReturn(ShopStatus.SUSPENDED);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 1)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — CLOSED 가게 → SHOP_INACTIVE")
    void createQrPayRequest_shopClosed_throws() {
        // given
        Shop shop = mock(Shop.class);
        given(shop.getStatus()).willReturn(ShopStatus.CLOSED);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 1)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — 존재하지 않는 메뉴 → MENU_NOT_FOUND")
    void createQrPayRequest_menuNotFound_throws() {
        // given — menuRepository가 빈 목록 반환 (해당 menuId 없음)
        Shop shop = createActiveShop();
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findAllById(List.of(MENU_ID_1))).willReturn(List.of());

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 1)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MENU_NOT_FOUND);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — 다른 가게 메뉴 요청 → MENU_NOT_FOUND")
    void createQrPayRequest_menuNotBelongToShop_throws() {
        // given — 메뉴가 존재하지만 다른 가게(shopId=99) 소속
        Shop shop = createActiveShop();
        Menu menuFromOtherShop = createMenu(MENU_ID_1, 99L, "다른가게메뉴", 3000L, true);

        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findAllById(List.of(MENU_ID_1))).willReturn(List.of(menuFromOtherShop));

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 1)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MENU_NOT_FOUND);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — 지갑 없음 → WALLET_NOT_FOUND")
    void createQrPayRequest_walletNotFound_throws() {
        // given
        Shop shop = createActiveShop();
        Menu menu = createMenu(MENU_ID_1, SHOP_ID, "떡볶이", 5000L, true);

        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findAllById(List.of(MENU_ID_1))).willReturn(List.of(menu));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 1)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WALLET_NOT_FOUND);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — 잔액 부족 → INSUFFICIENT_BALANCE")
    void createQrPayRequest_insufficientBalance_throws() {
        // given — 잔액 3,000원인데 5,000원짜리 메뉴 2개 요청 (10,000원)
        Shop shop = createActiveShop();
        Menu menu = createMenu(MENU_ID_1, SHOP_ID, "떡볶이", 5000L, true);

        Wallet lowWallet = createWallet(3_000L);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findAllById(List.of(MENU_ID_1))).willReturn(List.of(menu));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(lowWallet));

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 2)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — 품절 메뉴 → MENU_UNAVAILABLE")
    void createQrPayRequest_menuUnavailable_throws() {
        // given — isAvailable=false 메뉴
        Shop shop = createActiveShop();
        Menu unavailableMenu = createMenu(MENU_ID_1, SHOP_ID, "순대", 4000L, false);

        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findAllById(List.of(MENU_ID_1))).willReturn(List.of(unavailableMenu));

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 1)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MENU_UNAVAILABLE);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — 모든 메뉴 가격 0원 → ZERO_AMOUNT_NOT_ALLOWED")
    void createQrPayRequest_zeroTotalAmount_throws() {
        // given — 메뉴 price=0 데이터가 DB에 존재하는 경우 (데이터 오류)
        Shop shop = createActiveShop();
        Menu zeroMenu = createMenu(MENU_ID_1, SHOP_ID, "공짜메뉴", 0L, true);

        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findAllById(List.of(MENU_ID_1))).willReturn(List.of(zeroMenu));

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 3)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ZERO_AMOUNT_NOT_ALLOWED);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — totalAmount long 오버플로우 → INVALID_REQUEST")
    void createQrPayRequest_totalAmountOverflow_throws() {
        // given — Long.MAX_VALUE/2+1 가격 메뉴 2개 → addExact 시 ArithmeticException
        Shop shop = createActiveShop();
        Menu hugeMenu1 = createMenu(MENU_ID_1, SHOP_ID, "비싼메뉴1", Long.MAX_VALUE / 2 + 1, true);
        Menu hugeMenu2 = createMenu(MENU_ID_2, SHOP_ID, "비싼메뉴2", Long.MAX_VALUE / 2 + 1, true);

        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findAllById(List.of(MENU_ID_1, MENU_ID_2))).willReturn(List.of(hugeMenu1, hugeMenu2));

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 1),
                new QrPayItemRequest(MENU_ID_2, 1)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — 본인 가게에 결제 요청 → SELF_PAYMENT_NOT_ALLOWED")
    void createQrPayRequest_selfPayment_throws() {
        // given — shop.userId == userId (요청자가 해당 가게 상인 본인)
        Shop shop = mock(Shop.class);
        given(shop.getStatus()).willReturn(ShopStatus.ACTIVE);
        given(shop.getUserId()).willReturn(USER_ID); // 상인 ID = 결제자 ID
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 1)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SELF_PAYMENT_NOT_ALLOWED);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — pendingKey DB 충돌(동시 요청 레이스) → DUPLICATE_QR_PAY_REQUEST")
    void createQrPayRequest_duplicatePending_throws() {
        // given — saveAndFlush 시 pendingKey unique 제약 위반 (동시 요청 레이스 케이스)
        Shop shop = createActiveShop();
        Menu menu = createMenu(MENU_ID_1, SHOP_ID, "떡볶이", 5000L, true);
        Wallet wallet = createWallet(50_000L);

        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findAllById(List.of(MENU_ID_1))).willReturn(List.of(menu));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));
        given(qrPayRequestRepository.saveAndFlush(any(QrPayRequest.class)))
                .willThrow(new DataIntegrityViolationException("pending_key unique constraint"));

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 1)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_QR_PAY_REQUEST);
    }
}
