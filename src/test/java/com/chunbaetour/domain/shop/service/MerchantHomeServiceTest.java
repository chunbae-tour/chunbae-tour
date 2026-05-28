package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.repository.QrPayRequestRepository;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import com.chunbaetour.domain.shop.dto.response.MerchantHomeResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
    private static final String CACHE_KEY = "merchant:home:v1:" + USER_ID;

    @Test
    @DisplayName("상인 홈 조회 — 캐시 hit이면 DB 조회 없이 캐시를 반환한다")
    void getHome_cacheHit_returnsCachedResponse() throws Exception {
        MerchantHomeResponse cachedResponse = new MerchantHomeResponse(12_000L, java.time.LocalDate.of(2026, 5, 25), List.of());
        String cached = objectMapper.writeValueAsString(cachedResponse);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(cached);

        MerchantHomeResponse response = merchantHomeService.getHome(USER_ID);

        assertThat(response.todaySalesAmount()).isEqualTo(12_000L);
        assertThat(response.todaySalesDate()).isEqualTo(java.time.LocalDate.of(2026, 5, 25));
        verify(shopRepository, never()).findAllByUserId(any());
        verify(qrPayRequestRepository, never()).sumAmountByShopIdsAndStatusBetween(any(), any(), any(), any());
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("상인 홈 조회 — 캐시 miss이면 DB 조회 후 3분 TTL로 캐싱한다")
    void getHome_cacheMiss_loadsDbAndCaches() {
        Shop shop = createShop();
        QrPayRequest recentPayment = createCompletedQrPayRequest();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(shopRepository.findAllByUserId(USER_ID)).willReturn(List.of(shop));
        given(qrPayRequestRepository.sumAmountByShopIdsAndStatusBetween(
                eq(List.of(SHOP_ID)),
                eq(QrPayStatus.COMPLETED),
                eq(LocalDateTime.of(2026, 5, 24, 15, 0)),
                eq(LocalDateTime.of(2026, 5, 25, 15, 0))
        )).willReturn(15_000L);
        given(qrPayRequestRepository.findTop10ByShopIdInAndStatusAndCompletedAtBetweenOrderByCompletedAtDescIdDesc(
                List.of(SHOP_ID),
                QrPayStatus.COMPLETED,
                LocalDateTime.of(2026, 5, 24, 15, 0),
                LocalDateTime.of(2026, 5, 25, 15, 0)
        )).willReturn(List.of(recentPayment));

        MerchantHomeResponse response = merchantHomeService.getHome(USER_ID);

        assertThat(response.todaySalesAmount()).isEqualTo(15_000L);
        assertThat(response.todaySalesDate()).isEqualTo(java.time.LocalDate.of(2026, 5, 25));
        assertThat(response.recentPayments()).hasSize(1);
        assertThat(response.recentPayments().get(0).payRequestId()).isEqualTo("req-001");
        assertThat(response.recentPayments().get(0).completedAt())
                .isEqualTo(LocalDateTime.of(2026, 5, 25, 10, 10));
        verify(valueOperations).set(eq(CACHE_KEY), any(String.class), eq(Duration.ofMinutes(3)));
    }

    @Test
    @DisplayName("상인 홈 조회 — 가게가 없으면 0원과 빈 최근 결제 목록을 캐싱한다")
    void getHome_noShops_returnsEmptyDashboard() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(shopRepository.findAllByUserId(USER_ID)).willReturn(List.of());

        MerchantHomeResponse response = merchantHomeService.getHome(USER_ID);

        assertThat(response.todaySalesAmount()).isZero();
        assertThat(response.todaySalesDate()).isEqualTo(java.time.LocalDate.of(2026, 5, 25));
        assertThat(response.recentPayments()).isEmpty();
        verify(qrPayRequestRepository, never()).sumAmountByShopIdsAndStatusBetween(any(), any(), any(), any());
        verify(valueOperations).set(eq(CACHE_KEY), any(String.class), eq(Duration.ofMinutes(3)));
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

    private QrPayRequest createCompletedQrPayRequest() {
        QrPayRequest request = QrPayRequest.create(
                "req-001",
                1L,
                SHOP_ID,
                5_000L,
                "[]",
                LocalDateTime.of(2026, 5, 25, 10, 5)
        );
        ReflectionTestUtils.setField(request, "id", 1L);
        request.complete(LocalDateTime.of(2026, 5, 25, 10, 10));
        return request;
    }
}
