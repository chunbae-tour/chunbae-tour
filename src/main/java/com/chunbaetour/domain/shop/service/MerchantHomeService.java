package com.chunbaetour.domain.shop.service;

import static com.chunbaetour.domain.common.redis.MerchantHomeCacheKeys.CACHE_KEY_PREFIX;

import com.chunbaetour.domain.payment.repository.CompletedQrPayView;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    // 오늘 매출은 한국 영업일 기준으로 집계한다.
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    // 최근 결제 목록 노출 건수.
    private static final int RECENT_PAYMENT_LIMIT = 10;
    // 시간대별 매출 분포 칸 수(0~23시).
    private static final int HOURS_PER_DAY = 24;
    // 미완료 카운터 집계 대상 상태: 상인 거절 + 타임아웃 만료. 사용자 취소(CANCELLED)는 제외.
    private static final List<QrPayStatus> MISSED_STATUSES =
            List.of(QrPayStatus.REJECTED, QrPayStatus.EXPIRED);

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

        // 등록된 가게가 없으면 모든 메트릭을 빈 값으로 응답한다. 시간대 분포는 0으로 채운 24칸을 유지한다.
        if (shopIds.isEmpty()) {
            return new MerchantHomeResponse(0L, 0L, today, emptyHourlySales(), 0L, List.of());
        }

        // 한국 영업일 경계(어제 00:00 ~ 오늘 00:00 ~ 내일 00:00)를 DB 저장 기준 UTC LocalDateTime으로 변환한다.
        ZonedDateTime todayStartKst = today.atStartOfDay(BUSINESS_ZONE);
        LocalDateTime todayStart = toUtc(todayStartKst);
        LocalDateTime todayEnd = toUtc(todayStartKst.plusDays(1));
        LocalDateTime yesterdayStart = toUtc(todayStartKst.minusDays(1));

        // 오늘 완료된 결제 경량 목록을 한 번만 조회해 매출 합계·시간대 분포·최근 결제에 함께 재사용한다.
        // [트레이드오프] 시간대 분포가 당일 완료 전건을 요구해 LIMIT 없이 적재한다(menu_items 제외 경량 뷰).
        // 인덱스로 풀스캔은 해소됐으나 캐시 미스마다 적재량이 일 거래량에 비례 → 고거래 가게에서 힙/전송 부담.
        // 후속 최적화: 합계·시간버킷을 DB GROUP BY HOUR(CONVERT_TZ) + SUM으로 내리고 최근 결제만 LIMIT 10 분리.
        List<CompletedQrPayView> todayCompleted = qrPayRequestRepository
                .findCompletedByShopsBetween(shopIds, QrPayStatus.COMPLETED, todayStart, todayEnd);

        // 오늘 매출: 완료 결제 금액 합계.
        long todaySalesAmount = todayCompleted.stream()
                .mapToLong(CompletedQrPayView::getAmount)
                .sum();

        // 시간대별 매출 분포: KST 0~23시 24칸으로 버킷팅한다.
        List<MerchantHomeResponse.HourlySales> hourlySales = toHourlySales(todayCompleted);

        // 최근 결제: 이미 최신순으로 정렬된 목록에서 상위 10건만 사용한다.
        List<MerchantHomeResponse.RecentPaymentResponse> recentPayments = todayCompleted.stream()
                .limit(RECENT_PAYMENT_LIMIT)
                .map(MerchantHomeResponse.RecentPaymentResponse::from)
                .toList();

        // 어제 매출: 어제 영업일 전체(어제 00:00 ~ 오늘 00:00) COMPLETED 합계. 오늘 대비 비교 기준값.
        long yesterdaySalesAmount = Objects.requireNonNullElse(
                qrPayRequestRepository.sumAmountByShopIdsAndStatusBetween(
                        shopIds, QrPayStatus.COMPLETED, yesterdayStart, todayStart),
                0L);

        // 미완료 카운터: 오늘(expiredAt 기준) 거절(REJECTED)+만료(EXPIRED)로 끝난 건수. 사용자 취소(CANCELLED)는 상인 귀책이 아니라 제외한다.
        // expiredAt은 UTC로 저장돼 todayStart/todayEnd(UTC)와 존이 일관된다(createdAt은 KST라 사용 불가 — 쿼리 주석 참조).
        // [경계 5분 오차 수용] expiredAt = 접수 +5분이라, createdAt 기준 대비 윈도우가 5분 뒤로 밀린다.
        // KST 23:55~24:00 접수·거절 건은 expiredAt이 익일이라 당일 카운터에서 빠진다(일 경계 5분 엣지 — 무해로 수용).
        long missedPaymentCount = qrPayRequestRepository.countByShopsAndStatusesBetween(
                shopIds, MISSED_STATUSES, todayStart, todayEnd);

        return new MerchantHomeResponse(
                todaySalesAmount,
                yesterdaySalesAmount,
                today,
                hourlySales,
                missedPaymentCount,
                recentPayments
        );
    }

    /** KST 영업일 시각을 DB 저장 기준 UTC LocalDateTime으로 변환한다. */
    private static LocalDateTime toUtc(ZonedDateTime kst) {
        return kst.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * 완료 결제 목록을 KST 기준 0~23시 24칸 매출 분포로 변환한다.
     * completedAt은 UTC로 저장되므로 KST로 환산한 시(hour)에 금액을 누적하고, 매출이 없는 시간대도 0으로 채운다.
     */
    private static List<MerchantHomeResponse.HourlySales> toHourlySales(List<CompletedQrPayView> completed) {
        long[] buckets = new long[HOURS_PER_DAY];
        for (CompletedQrPayView view : completed) {
            int hour = view.getCompletedAt()
                    .atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(BUSINESS_ZONE)
                    .getHour();
            buckets[hour] += view.getAmount();
        }

        List<MerchantHomeResponse.HourlySales> hourlySales = new ArrayList<>(HOURS_PER_DAY);
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            hourlySales.add(new MerchantHomeResponse.HourlySales(hour, buckets[hour]));
        }
        return hourlySales;
    }

    /** 가게가 없는 상인에게도 동일한 24칸 분포 구조를 유지하기 위한 0 채움 분포. */
    private static List<MerchantHomeResponse.HourlySales> emptyHourlySales() {
        return toHourlySales(List.of());
    }
}
