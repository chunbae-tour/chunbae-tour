package com.chunbaetour.domain.admin.dashboard.service;

import com.chunbaetour.domain.admin.dashboard.dto.response.AdminDashboardResponse;
import com.chunbaetour.domain.admin.user.service.AdminUserService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 운영자 대시보드 카운트 요약 서비스 (KAN-181, Admin Epic KAN-177 S03).
 *
 * <p><b>조합 책임만</b>: 카운트 쿼리는 {@link AdminUserService}에만 두고 본 서비스는 결과 3종을 조합한다
 * (직접 레포 쿼리 금지). 후속 슬라이스가 가게/콘텐츠 카운트를 추가할 때도 본 서비스는 해당 도메인 서비스
 * 호출만 늘리는 점진 패턴을 유지한다.
 *
 * <p><b>graceful degradation</b>: Redis 캐시는 {@code MerchantHomeService} 패턴을 미러링한다 —
 * Redis 장애/역직렬화 실패 시 예외를 전파하지 않고 DB 조합으로 폴백해 항상 200을 반환한다.
 * ({@code @Cacheable} 미사용 — 장애 시 throw되므로 수동 try/catch가 필요.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    // 대시보드 카운트는 정확도보다 응답 속도를 우선 — Redis에 1분 저장 (PRD §16).
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);
    // 모든 운영자가 동일 카운트를 보므로 사용자별 분리 없는 단일 전역 키.
    private static final String CACHE_KEY = "admin:dashboard:summary";

    private final AdminUserService adminUserService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 대시보드 요약 조회. Redis 캐시를 먼저 확인하고, miss/장애 시 DB 조합 후 1분 캐싱한다.
     *
     * @return 사용자 카운트 3종 요약 (Redis 장애 시에도 DB 값으로 반환)
     */
    public AdminDashboardResponse getSummary() {
        // 1. Redis 캐시 우선 조회. 값이 있으면 DB 집계 없이 즉시 반환.
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, AdminDashboardResponse.class);
                } catch (JacksonException e) {
                    // 캐시 데이터 손상 — 장애로 처리하지 않고 DB 폴백.
                    log.warn("[admin 대시보드] 캐시 역직렬화 실패, DB 폴백");
                }
            }
        } catch (Exception e) {
            // Redis 장애가 있어도 대시보드 조회는 DB로 계속 제공.
            log.warn("[admin 대시보드] Redis 조회 실패, DB 폴백", e);
        }

        // 2. 캐시 miss 또는 Redis 실패 시 DB 조합.
        AdminDashboardResponse response = loadSummary();

        // 3. DB 결과를 Redis에 1분 TTL 저장. 저장 실패는 응답 실패로 보지 않음.
        try {
            redisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (Exception e) {
            log.warn("[admin 대시보드] 캐시 저장 실패 — DB 응답은 정상", e);
        }
        return response;
    }

    /** 사용자 카운트 3종을 {@link AdminUserService}에서 조합. */
    private AdminDashboardResponse loadSummary() {
        return new AdminDashboardResponse(
                adminUserService.getTotalUsers(),
                adminUserService.getNewUsersToday(),
                adminUserService.getSuspendedUsers());
    }
}
