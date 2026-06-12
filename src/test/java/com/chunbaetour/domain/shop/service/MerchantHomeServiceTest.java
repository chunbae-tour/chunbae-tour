package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.payment.repository.CompletedQrPayView;
import com.chunbaetour.domain.payment.repository.QrPayRequestRepository;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import com.chunbaetour.domain.shop.dto.response.MerchantHomeResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MerchantHomeServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private QrPayRequestRepository qrPayRequestRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-05-24T15:30:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private MerchantHomeService merchantHomeService;

    private static final Long USER_ID = 99L;
    private static final Long SHOP_ID = 10L;
    private static final String CACHE_KEY = "merchant:home:v2:" + USER_ID;

    // clock=2026-05-24T15:30Z → KST 2026-05-25 → 오늘 영업일 경계(UTC)
    private static final LocalDateTime TODAY_START = LocalDateTime.of(2026, 5, 24, 15, 0);
    private static final LocalDateTime TODAY_END = LocalDateTime.of(2026, 5, 25, 15, 0);
    private static final LocalDateTime YESTERDAY_START = LocalDateTime.of(2026, 5, 23, 15, 0);

    @Test
    @DisplayName("상인 홈 조회 — 캐시 hit이면 DB 조회 없이 캐시를 반환한다")
    void getHome_cacheHit_returnsCachedResponse() throws Exception {
        MerchantHomeResponse cachedResponse = new MerchantHomeResponse(
                12_000L,
                9_000L,
                LocalDate.of(2026, 5, 25),
                List.of(new MerchantHomeResponse.HourlySales(10, 12_000L)),
                2L,
                List.of(new MerchantHomeResponse.RecentPaymentResponse(
                        "req-001",
                        SHOP_ID,
                        5_000L,
                        LocalDateTime.of(2026, 5, 25, 10, 10)
                ))
        );
        String cached = objectMapper.writeValueAsString(cachedResponse);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(cached);

        MerchantHomeResponse response = merchantHomeService.getHome(USER_ID);

        assertThat(response.todaySalesAmount()).isEqualTo(12_000L);
        assertThat(response.yesterdaySalesAmount()).isEqualTo(9_000L);
        assertThat(response.todaySalesDate()).isEqualTo(LocalDate.of(2026, 5, 25));
        assertThat(response.missedPaymentCount()).isEqualTo(2L);
        assertThat(response.recentPayments()).hasSize(1);
        assertThat(response.recentPayments().get(0).completedAt())
                .isEqualTo(LocalDateTime.of(2026, 5, 25, 10, 10));
        verify(shopRepository, never()).findAllByUserId(any());
        verify(qrPayRequestRepository, never())
                .findCompletedByShopsBetween(any(), any(), any(), any());
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("상인 홈 조회 — 캐시 miss이면 매출·어제대비·미완료 메트릭을 DB 조회 후 3분 TTL로 캐싱한다")
    void getHome_cacheMiss_loadsMetricsAndCaches() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(shopRepository.findAllByUserId(USER_ID)).willReturn(List.of(createShop()));
        // 오늘 완료 결제 2건 (KST 10시 5,000 / KST 14시 10,000) — 최신순으로 반환
        given(qrPayRequestRepository.findCompletedByShopsBetween(
                eq(List.of(SHOP_ID)), eq(QrPayStatus.COMPLETED), eq(TODAY_START), eq(TODAY_END)
        )).willReturn(List.of(
                view("req-002", 10_000L, LocalDateTime.of(2026, 5, 25, 5, 0)),   // UTC 05:00 → KST 14시
                view("req-001", 5_000L, LocalDateTime.of(2026, 5, 25, 1, 10))     // UTC 01:10 → KST 10시
        ));
        given(qrPayRequestRepository.sumAmountByShopIdsAndStatusBetween(
                eq(List.of(SHOP_ID)), eq(QrPayStatus.COMPLETED), eq(YESTERDAY_START), eq(TODAY_START)
        )).willReturn(20_000L);
        given(qrPayRequestRepository.countByShopsAndStatusesBetween(
                eq(List.of(SHOP_ID)),
                eq(List.of(QrPayStatus.REJECTED, QrPayStatus.EXPIRED)),
                eq(TODAY_START), eq(TODAY_END)
        )).willReturn(3L);

        MerchantHomeResponse response = merchantHomeService.getHome(USER_ID);

        assertThat(response.todaySalesAmount()).isEqualTo(15_000L);
        assertThat(response.yesterdaySalesAmount()).isEqualTo(20_000L);
        assertThat(response.missedPaymentCount()).isEqualTo(3L);
        assertThat(response.hourlySales()).hasSize(24);
        assertThat(response.hourlySales().get(10).amount()).isEqualTo(5_000L);
        assertThat(response.hourlySales().get(14).amount()).isEqualTo(10_000L);
        assertThat(response.recentPayments()).hasSize(2);
        assertThat(response.recentPayments().get(0).payRequestId()).isEqualTo("req-002");
        verify(valueOperations).set(eq(CACHE_KEY), any(String.class), eq(Duration.ofMinutes(3)));
    }

    @Test
    @DisplayName("상인 홈 조회 — 시간대별 분포는 UTC 저장 시각을 KST 0~23시로 환산해 버킷팅한다")
    void getHome_hourlySales_bucketsByKstHour() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(shopRepository.findAllByUserId(USER_ID)).willReturn(List.of(createShop()));
        given(qrPayRequestRepository.findCompletedByShopsBetween(
                eq(List.of(SHOP_ID)), eq(QrPayStatus.COMPLETED), eq(TODAY_START), eq(TODAY_END)
        )).willReturn(List.of(
                view("req-late", 7_000L, LocalDateTime.of(2026, 5, 25, 14, 0)),  // UTC 14:00 → KST 23시
                view("req-open", 3_000L, LocalDateTime.of(2026, 5, 24, 15, 30))  // UTC 15:30 → KST 00시
        ));

        MerchantHomeResponse response = merchantHomeService.getHome(USER_ID);

        assertThat(response.hourlySales()).hasSize(24);
        assertThat(response.hourlySales().get(0).amount()).isEqualTo(3_000L);
        assertThat(response.hourlySales().get(23).amount()).isEqualTo(7_000L);
        // 그 외 시간대는 0으로 채워진다.
        assertThat(response.hourlySales().get(12).amount()).isZero();
    }

    @Test
    @DisplayName("상인 홈 조회 — 가게가 없으면 0 매출·24칸 빈 분포·미완료 0을 캐싱한다")
    void getHome_noShops_returnsEmptyDashboard() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(shopRepository.findAllByUserId(USER_ID)).willReturn(List.of());

        MerchantHomeResponse response = merchantHomeService.getHome(USER_ID);

        assertThat(response.todaySalesAmount()).isZero();
        assertThat(response.yesterdaySalesAmount()).isZero();
        assertThat(response.missedPaymentCount()).isZero();
        assertThat(response.hourlySales()).hasSize(24);
        assertThat(response.hourlySales()).allMatch(h -> h.amount() == 0L);
        assertThat(response.recentPayments()).isEmpty();
        verify(qrPayRequestRepository, never())
                .findCompletedByShopsBetween(any(), any(), any(), any());
        verify(qrPayRequestRepository, never())
                .countByShopsAndStatusesBetween(any(), any(), any(), any());
        verify(valueOperations).set(eq(CACHE_KEY), any(String.class), eq(Duration.ofMinutes(3)));
    }

    @Test
    @DisplayName("상인 홈 조회 — KST 자정 정각이면 다음 영업일 시작 시각을 사용한다")
    void getHome_kstMidnight_usesNextBusinessDay() {
        Clock midnightClock = Clock.fixed(Instant.parse("2026-05-24T15:00:00Z"), ZoneOffset.UTC);
        MerchantHomeService midnightService = new MerchantHomeService(
                shopRepository, qrPayRequestRepository, redisTemplate, objectMapper, midnightClock);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(shopRepository.findAllByUserId(USER_ID)).willReturn(List.of(createShop()));
        given(qrPayRequestRepository.findCompletedByShopsBetween(
                eq(List.of(SHOP_ID)), eq(QrPayStatus.COMPLETED), eq(TODAY_START), eq(TODAY_END)
        )).willReturn(List.of());
        given(qrPayRequestRepository.sumAmountByShopIdsAndStatusBetween(
                eq(List.of(SHOP_ID)), eq(QrPayStatus.COMPLETED), eq(YESTERDAY_START), eq(TODAY_START)
        )).willReturn(0L);
        given(qrPayRequestRepository.countByShopsAndStatusesBetween(
                eq(List.of(SHOP_ID)),
                eq(List.of(QrPayStatus.REJECTED, QrPayStatus.EXPIRED)),
                eq(TODAY_START), eq(TODAY_END)
        )).willReturn(0L);

        MerchantHomeResponse response = midnightService.getHome(USER_ID);

        assertThat(response.todaySalesDate()).isEqualTo(LocalDate.of(2026, 5, 25));
        assertThat(response.todaySalesAmount()).isZero();
    }

    private Shop createShop() {
        Shop shop = Shop.builder()
                .userId(USER_ID)
                .applicationId(1L)
                .shopName("광화문 떡볶이")
                .category("FOOD")
                .address("서울 종로구")
                .build();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        return shop;
    }

    /** 완료 QR 결제 경량 뷰 테스트 더블. 인터페이스 프로젝션을 record로 구현해 불필요 stubbing 없이 사용한다. */
    private static CompletedQrPayView view(String payRequestId, long amount, LocalDateTime completedAt) {
        return new CompletedView(payRequestId, SHOP_ID, amount, completedAt);
    }

    private record CompletedView(String payRequestId, Long shopId, Long amount, LocalDateTime completedAt)
            implements CompletedQrPayView {
        @Override
        public String getPayRequestId() {
            return payRequestId;
        }

        @Override
        public Long getShopId() {
            return shopId;
        }

        @Override
        public Long getAmount() {
            return amount;
        }

        @Override
        public LocalDateTime getCompletedAt() {
            return completedAt;
        }
    }
}
