package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.repository.QrPayRequestRepository;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import com.chunbaetour.domain.shop.dto.response.MerchantHomeResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 상인 홈 대시보드 조회 서비스.
 * 상인이 운영하는 모든 가게의 오늘 QR 결제 매출 합계와 최근 결제 목록을 제공한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantHomeService {

    // 상인 홈 데이터는 정확도보다 응답 속도를 우선하므로 Redis에 3분 동안 저장한다.
    private static final Duration CACHE_TTL = Duration.ofMinutes(3);
    // 캐시 키 버전 프리픽스. 스키마가 바뀌면 v2로 올려 이전 캐시와 충돌을 피한다.
    private static final String CACHE_KEY_PREFIX = "merchant:home:v1:";
    // 오늘 매출은 한국 영업일 기준으로 집계한다.
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final ShopRepository shopRepository;
    private final QrPayRequestRepository qrPayRequestRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 상인 홈 데이터를 조회한다.
     * Redis 캐시를 먼저 확인하고, 캐시가 없으면 DB 조회 후 3분 동안 캐싱한다.
     *
     * @param userId 인증된 상인 사용자 ID
     * @return 상인 홈 대시보드 응답
     */
    public MerchantHomeResponse getHome(Long userId) {
        // 1. 상인 userId 단위로 캐시 키를 만든다.
        String cacheKey = CACHE_KEY_PREFIX + userId;

        // 2. Redis 캐시를 먼저 조회한다. 캐시가 있으면 DB 집계 없이 즉시 반환한다.
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    // JSON 문자열로 저장된 캐시 값을 응답 DTO로 복원한다.
                    return objectMapper.readValue(cached, MerchantHomeResponse.class);
                } catch (JacksonException e) {
                    // 캐시 데이터가 깨진 경우에는 장애로 처리하지 않고 DB 조회로 폴백한다.
                    log.warn("[상인 홈] 캐시 역직렬화 실패, DB 폴백 (userId: {})", userId);
                }
            }
        } catch (Exception e) {
            // Redis 장애가 있어도 상인 홈 조회 자체는 DB로 계속 제공한다.
            log.warn("[상인 홈] Redis 조회 실패, DB 폴백 (userId: {})", userId, e);
        }

        // 3. 캐시 miss 또는 Redis 실패 시 DB에서 상인 홈 데이터를 조회한다.
        MerchantHomeResponse response = loadHome(userId);

        // 4. DB 조회 결과를 Redis에 3분 TTL로 저장한다. 저장 실패는 응답 실패로 보지 않는다.
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (Exception e) {
            log.warn("[상인 홈] 캐시 저장 실패 — DB 응답은 정상 (userId: {})", userId, e);
        }
        return response;
    }

    /**
     * 상인이 소유한 모든 가게를 기준으로 오늘 매출 합계와 최근 결제 목록을 만든다.
     *
     * <p>상인 1명이 여러 가게를 운영할 수 있으므로 가게 단건이 아니라 userId 기준
     * 전체 가게를 조회해서 대시보드 요약을 생성한다.
     *
     * @param userId 인증된 상인 사용자 ID
     * @return DB 조회 결과로 만든 상인 홈 응답
     */
    private MerchantHomeResponse loadHome(Long userId) {
        // 상인이 소유한 모든 가게 ID를 조회한다. 상인 1명은 여러 가게를 운영할 수 있다.
        List<Long> shopIds = shopRepository.findAllByUserId(userId).stream()
                .map(Shop::getId)
                .toList();

        // 한국 영업일 기준 오늘 날짜를 먼저 구한다. 빈 가게 응답에도 같은 기준 날짜를 내려준다.
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));

        // 등록된 가게가 없으면 매출 0원, 최근 결제 빈 목록으로 응답한다.
        if (shopIds.isEmpty()) {
            return new MerchantHomeResponse(0L, today, List.of());
        }

        // 한국 영업일 기준 오늘 00:00 이상, 내일 00:00 미만을 DB 저장 기준인 UTC LocalDateTime으로 변환한다.
        ZonedDateTime startKst = today.atStartOfDay(BUSINESS_ZONE);
        LocalDateTime startAt = startKst.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime endAt = startKst.plusDays(1).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        // 오늘 매출: 모든 내 가게의 COMPLETED QR 결제 금액을 합산한다.
        long todaySalesAmount = Objects.requireNonNullElse(
                qrPayRequestRepository.sumAmountByShopIdsAndStatusBetween(
                        shopIds, QrPayStatus.COMPLETED, startAt, endAt),
                0L);

        // 오늘 완료된 결제 중 최신 10건만 가져온다. 오늘 매출과 같은 날짜 범위를 유지한다.
        List<QrPayRequest> recentPayments = qrPayRequestRepository
                .findTop10ByShopIdInAndStatusAndCompletedAtBetweenOrderByCompletedAtDescIdDesc(
                        shopIds, QrPayStatus.COMPLETED, startAt, endAt);

        // 엔티티를 API 응답 DTO로 변환한다.
        return new MerchantHomeResponse(
                todaySalesAmount,
                today,
                recentPayments.stream()
                        .map(MerchantHomeResponse.RecentPaymentResponse::from)
                        .toList()
        );
    }
}
