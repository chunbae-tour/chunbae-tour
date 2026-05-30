package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.dto.request.QrPayConfirmRequest;
import com.chunbaetour.domain.payment.dto.request.QrPayCreateRequest;
import com.chunbaetour.domain.payment.dto.request.QrPayItemRequest;
import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse;
import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.repository.QrPayRequestRepository;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.MenuRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

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

    @Mock
    private ShopWalletRepository shopWalletRepository;

    @Mock
    private YeopjeonHistoryRepository yeopjeonHistoryRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private MessageSource messageSource;

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
    private static final String PAY_REQUEST_ID = "req-001";
    private static final Long AMOUNT = 5_000L;
    // 고정 Clock(10:00:00) 기준: NOT_EXPIRED > 10:00, EXPIRED_AT < 10:00
    private static final LocalDateTime NOT_EXPIRED = LocalDateTime.of(2026, 5, 25, 10, 30, 0);
    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2026, 5, 25, 9, 55, 0);

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
        lenient().when(wallet.getBalance()).thenReturn(balance);
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

    private QrPayRequest createPendingRequest(LocalDateTime expiredAt) {
        return QrPayRequest.create(PAY_REQUEST_ID, USER_ID, SHOP_ID, AMOUNT, "[]", expiredAt);
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
    @DisplayName("QR 결제 요청 생성 — 메뉴 가격 0원(DB 데이터 오류) → INVALID_REQUEST")
    void createQrPayRequest_zeroPriceMenu_throws() {
        // given — price=0 메뉴가 DB에 존재하는 경우, price <= 0 방어에서 차단
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
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("QR 결제 요청 생성 — 동일 사용자·가게에 PENDING 존재(사전 체크) → DUPLICATE_QR_PAY_REQUEST")
    void createQrPayRequest_existingPendingRequest_throws() {
        // given — existsBy 사전 체크에서 차단 (일반 중복 요청 경로)
        Shop shop = createActiveShop();
        Menu menu = createMenu(MENU_ID_1, SHOP_ID, "떡볶이", 5000L, true);

        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findAllById(List.of(MENU_ID_1))).willReturn(List.of(menu));
        given(qrPayRequestRepository.existsByUserIdAndShopIdAndStatusAndExpiredAtAfter(
                eq(USER_ID), eq(SHOP_ID), eq(QrPayStatus.PENDING), any(LocalDateTime.class)))
                .willReturn(true);

        QrPayCreateRequest request = new QrPayCreateRequest(SHOP_ID, List.of(
                new QrPayItemRequest(MENU_ID_1, 1)
        ));

        // then
        assertThatThrownBy(() -> qrPayService.createQrPayRequest(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_QR_PAY_REQUEST);
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

    // ── PATCH /payments/qr/{payRequestId}/confirm ──────────────────────────────

    @Test
    @DisplayName("QR 결제 승인 — 성공: 잔액 차감·증가, 이력 2건, COMPLETED 전이")
    void confirmQrPayRequest_approve_success() throws InterruptedException {
        // given
        QrPayRequest req = createPendingRequest(NOT_EXPIRED);
        Shop shop = createActiveShop();
        Wallet wallet = createWallet(AMOUNT);
        ShopWallet shopWallet = mock(ShopWallet.class);
        given(shopWallet.getBalance()).willReturn(0L);

        RLock lock = mock(RLock.class);
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        given(qrPayRequestRepository.findByPayRequestId(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(qrPayRequestRepository.findByPayRequestIdWithLock(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(walletRepository.findByUserIdWithLock(USER_ID)).willReturn(Optional.of(wallet));
        given(shopWalletRepository.findByShopIdWithLock(SHOP_ID)).willReturn(Optional.of(shopWallet));
        given(yeopjeonHistoryRepository.save(any())).willAnswer(i -> i.getArgument(0));

        QrPayConfirmRequest request = new QrPayConfirmRequest(QrPayConfirmRequest.Action.APPROVE, null);

        // when
        qrPayService.confirmQrPayRequest(MERCHANT_USER_ID, PAY_REQUEST_ID, request);

        // then
        then(wallet).should().debit(AMOUNT);
        then(shopWallet).should().credit(AMOUNT);
        then(yeopjeonHistoryRepository).should(times(2)).save(any());
        assertThat(req.getStatus()).isEqualTo(QrPayStatus.COMPLETED);
        then(redisTemplate).should().delete("merchant:home:v1:" + MERCHANT_USER_ID);
        then(lock).should().unlock();
        // 데드락 방지 락 순서 고정: wallets → shop_wallets
        InOrder order = inOrder(walletRepository, shopWalletRepository);
        order.verify(walletRepository).findByUserIdWithLock(USER_ID);
        order.verify(shopWalletRepository).findByShopIdWithLock(SHOP_ID);
    }

    @Test
    @DisplayName("QR 결제 거절 — 성공: 락 이후 REJECTED 전이, 잔액 조작 없음, 거절 사유 저장")
    void confirmQrPayRequest_reject_success() throws InterruptedException {
        // given
        QrPayRequest req = createPendingRequest(NOT_EXPIRED);
        Shop shop = createActiveShop();

        RLock lock = mock(RLock.class);
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        given(qrPayRequestRepository.findByPayRequestId(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(qrPayRequestRepository.findByPayRequestIdWithLock(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        QrPayConfirmRequest request = new QrPayConfirmRequest(QrPayConfirmRequest.Action.REJECT, "재고 없음");

        // when
        qrPayService.confirmQrPayRequest(MERCHANT_USER_ID, PAY_REQUEST_ID, request);

        // then — 잔액 조작 없음, 락은 획득 후 해제
        then(walletRepository).should(never()).findByUserIdWithLock(anyLong());
        then(lock).should().unlock();
        assertThat(req.getStatus()).isEqualTo(QrPayStatus.REJECTED);
        assertThat(req.getRejectReason()).isEqualTo("재고 없음");
    }

    @Test
    @DisplayName("QR 결제 승인/거절 — 결제 요청 없음 → QR_PAY_REQUEST_NOT_FOUND")
    void confirmQrPayRequest_payRequestNotFound_throws() {
        // given
        given(qrPayRequestRepository.findByPayRequestId(PAY_REQUEST_ID)).willReturn(Optional.empty());

        QrPayConfirmRequest request = new QrPayConfirmRequest(QrPayConfirmRequest.Action.APPROVE, null);

        // then
        assertThatThrownBy(() -> qrPayService.confirmQrPayRequest(MERCHANT_USER_ID, PAY_REQUEST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QR_PAY_REQUEST_NOT_FOUND);
    }

    @Test
    @DisplayName("QR 결제 승인/거절 — 가게 없음 → SHOP_NOT_FOUND")
    void confirmQrPayRequest_shopNotFound_throws() {
        // given
        QrPayRequest req = createPendingRequest(NOT_EXPIRED);
        given(qrPayRequestRepository.findByPayRequestId(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.empty());

        QrPayConfirmRequest request = new QrPayConfirmRequest(QrPayConfirmRequest.Action.APPROVE, null);

        // then
        assertThatThrownBy(() -> qrPayService.confirmQrPayRequest(MERCHANT_USER_ID, PAY_REQUEST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("QR 결제 승인/거절 — 타 상인의 가게 → QR_PAY_CONFIRM_FORBIDDEN")
    void confirmQrPayRequest_forbidden_throws() {
        // given — 가게 소유자(MERCHANT_USER_ID=99)와 요청자(999)가 다름
        QrPayRequest req = createPendingRequest(NOT_EXPIRED);
        Shop shop = createActiveShop(); // shop.userId == MERCHANT_USER_ID(99)

        given(qrPayRequestRepository.findByPayRequestId(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        Long wrongMerchantId = 999L;
        QrPayConfirmRequest request = new QrPayConfirmRequest(QrPayConfirmRequest.Action.APPROVE, null);

        // then
        assertThatThrownBy(() -> qrPayService.confirmQrPayRequest(wrongMerchantId, PAY_REQUEST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QR_PAY_CONFIRM_FORBIDDEN);
        then(redissonClient).should(never()).getLock(anyString());
    }

    @Test
    @DisplayName("QR 결제 승인/거절 — 승인 시점 가게 영업 정지 → SHOP_INACTIVE, 락 미획득")
    void confirmQrPayRequest_shopInactive_throws() {
        // given — 소유권은 맞지만 가게가 SUSPENDED 상태
        QrPayRequest req = createPendingRequest(NOT_EXPIRED);
        Shop shop = createActiveShop();
        ReflectionTestUtils.setField(shop, "status", ShopStatus.SUSPENDED);

        given(qrPayRequestRepository.findByPayRequestId(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        QrPayConfirmRequest request = new QrPayConfirmRequest(QrPayConfirmRequest.Action.APPROVE, null);

        // then
        assertThatThrownBy(() -> qrPayService.confirmQrPayRequest(MERCHANT_USER_ID, PAY_REQUEST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
        then(redissonClient).should(never()).getLock(anyString());
    }

    @Test
    @DisplayName("QR 결제 승인/거절 — 만료된 요청 → QR_PAY_INVALID_STATUS_TRANSITION")
    void confirmQrPayRequest_expired_throws() {
        // given — expiredAt(9:55) < clock.now(10:00) → 만료 처리됨
        QrPayRequest req = createPendingRequest(EXPIRED_AT);
        Shop shop = createActiveShop();

        given(qrPayRequestRepository.findByPayRequestId(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        QrPayConfirmRequest request = new QrPayConfirmRequest(QrPayConfirmRequest.Action.APPROVE, null);

        // then
        assertThatThrownBy(() -> qrPayService.confirmQrPayRequest(MERCHANT_USER_ID, PAY_REQUEST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QR_PAY_INVALID_STATUS_TRANSITION);
        then(redissonClient).should(never()).getLock(anyString());
    }

    @Test
    @DisplayName("QR 결제 승인 — 잔액 부족 → INSUFFICIENT_BALANCE")
    void confirmQrPayRequest_insufficientBalance_throws() throws InterruptedException {
        // given — 지갑 잔액(1,000)이 결제 금액(5,000)보다 적음
        QrPayRequest req = createPendingRequest(NOT_EXPIRED);
        Shop shop = createActiveShop();
        Wallet lowWallet = createWallet(1_000L);
        ShopWallet shopWallet = mock(ShopWallet.class);

        RLock lock = mock(RLock.class);
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        given(qrPayRequestRepository.findByPayRequestId(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(qrPayRequestRepository.findByPayRequestIdWithLock(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(walletRepository.findByUserIdWithLock(USER_ID)).willReturn(Optional.of(lowWallet));
        given(shopWalletRepository.findByShopIdWithLock(SHOP_ID)).willReturn(Optional.of(shopWallet));

        QrPayConfirmRequest request = new QrPayConfirmRequest(QrPayConfirmRequest.Action.APPROVE, null);

        // then
        assertThatThrownBy(() -> qrPayService.confirmQrPayRequest(MERCHANT_USER_ID, PAY_REQUEST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        then(lock).should().unlock(); // finally 블록에서 반드시 해제
    }

    @Test
    @DisplayName("QR 결제 승인 — 상인 지갑 없음 → SHOP_WALLET_NOT_FOUND, 락 해제")
    void confirmQrPayRequest_shopWalletNotFound_throws() throws InterruptedException {
        // given
        QrPayRequest req = createPendingRequest(NOT_EXPIRED);
        Shop shop = createActiveShop();
        Wallet wallet = createWallet(AMOUNT);

        RLock lock = mock(RLock.class);
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        given(qrPayRequestRepository.findByPayRequestId(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(qrPayRequestRepository.findByPayRequestIdWithLock(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));
        given(walletRepository.findByUserIdWithLock(USER_ID)).willReturn(Optional.of(wallet));
        given(shopWalletRepository.findByShopIdWithLock(SHOP_ID)).willReturn(Optional.empty());

        QrPayConfirmRequest request = new QrPayConfirmRequest(QrPayConfirmRequest.Action.APPROVE, null);

        // then
        assertThatThrownBy(() -> qrPayService.confirmQrPayRequest(MERCHANT_USER_ID, PAY_REQUEST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_WALLET_NOT_FOUND);
        then(lock).should().unlock();
    }

    @Test
    @DisplayName("QR 결제 승인 — 분산 락 타임아웃 → PAYMENT_PROCESSING")
    void confirmQrPayRequest_lockTimeout_throws() throws InterruptedException {
        // given — lock.tryLock 이 false 반환 → 다른 요청이 락 점유 중
        QrPayRequest req = createPendingRequest(NOT_EXPIRED);
        Shop shop = createActiveShop();

        RLock lock = mock(RLock.class);
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(false);
        given(lock.isHeldByCurrentThread()).willReturn(false);

        given(qrPayRequestRepository.findByPayRequestId(PAY_REQUEST_ID)).willReturn(Optional.of(req));
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        QrPayConfirmRequest request = new QrPayConfirmRequest(QrPayConfirmRequest.Action.APPROVE, null);

        // then
        assertThatThrownBy(() -> qrPayService.confirmQrPayRequest(MERCHANT_USER_ID, PAY_REQUEST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_PROCESSING);
        then(lock).should(never()).unlock(); // 락 미획득 시 unlock 호출 없음
    }

    // ── getPendingQrPayments ──────────────────────────────────────────────────

    @Test
    @DisplayName("대기중 QR 결제 목록 조회 — PENDING 요청 목록 반환")
    void getPendingQrPayments_returnsPendingList() {
        Shop shop = createActiveShop(); // SHOP_ID = 10L
        QrPayRequest req1 = mock(QrPayRequest.class);
        given(req1.getPayRequestId()).willReturn("req-pending-1");
        given(req1.getShopId()).willReturn(SHOP_ID);
        given(req1.getUserId()).willReturn(USER_ID);
        given(req1.getAmount()).willReturn(3_000L);
        given(req1.getMenuItems()).willReturn("[{\"menuId\":100}]");
        given(req1.getCreatedAt()).willReturn(NOT_EXPIRED.minusMinutes(3));
        given(req1.getExpiredAt()).willReturn(NOT_EXPIRED);

        given(shopRepository.findAllByUserId(MERCHANT_USER_ID)).willReturn(List.of(shop));
        given(qrPayRequestRepository.findPendingByShopIds(
                eq(List.of(SHOP_ID)), eq(QrPayStatus.PENDING), any(LocalDateTime.class)))
                .willReturn(List.of(req1));

        var result = qrPayService.getPendingQrPayments(MERCHANT_USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).payRequestId()).isEqualTo("req-pending-1");
        assertThat(result.get(0).shopId()).isEqualTo(SHOP_ID);
    }

    @Test
    @DisplayName("대기중 QR 결제 목록 조회 — 가게 없으면 빈 목록")
    void getPendingQrPayments_noShops_returnsEmpty() {
        given(shopRepository.findAllByUserId(MERCHANT_USER_ID)).willReturn(List.of());

        var result = qrPayService.getPendingQrPayments(MERCHANT_USER_ID);

        assertThat(result).isEmpty();
        then(qrPayRequestRepository).should(never())
                .findPendingByShopIds(any(), any(), any());
    }

    @Test
    @DisplayName("대기중 QR 결제 목록 조회 — PENDING 요청 없으면 빈 목록")
    void getPendingQrPayments_noPendingRequests_returnsEmpty() {
        Shop shop = createActiveShop();
        given(shopRepository.findAllByUserId(MERCHANT_USER_ID)).willReturn(List.of(shop));
        given(qrPayRequestRepository.findPendingByShopIds(
                eq(List.of(SHOP_ID)), eq(QrPayStatus.PENDING), any(LocalDateTime.class)))
                .willReturn(List.of());

        var result = qrPayService.getPendingQrPayments(MERCHANT_USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("대기중 QR 결제 목록 조회 — expiredAt > now 조건 전달하여 만료 건 DB에서 제외")
    void getPendingQrPayments_passesNowToRepository() {
        Shop shop = createActiveShop();
        given(shopRepository.findAllByUserId(MERCHANT_USER_ID)).willReturn(List.of(shop));
        given(qrPayRequestRepository.findPendingByShopIds(
                eq(List.of(SHOP_ID)), eq(QrPayStatus.PENDING), any(LocalDateTime.class)))
                .willReturn(List.of());

        qrPayService.getPendingQrPayments(MERCHANT_USER_ID);

        // now 파라미터가 null 아닌 값으로 전달됐는지 검증 — null이면 만료 건 필터링 안 됨
        then(qrPayRequestRepository).should()
                .findPendingByShopIds(eq(List.of(SHOP_ID)), eq(QrPayStatus.PENDING), any(LocalDateTime.class));
    }
}
