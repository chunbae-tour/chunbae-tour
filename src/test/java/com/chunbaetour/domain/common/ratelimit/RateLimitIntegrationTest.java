package com.chunbaetour.domain.common.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Rate Limit end-to-end 통합 테스트.
 *
 * <p>{@link AbstractIntegrationTest}는 기본적으로 {@code ratelimit.enabled=false}로 비활성화한다 (다른 통합 테스트
 * 회귀 방지). 본 클래스는 {@link DynamicPropertySource}로 짧은 정책 + enabled=true 강제 활성화하여
 * Rate Limit 자체 동작만 격리 검증.
 *
 * <p>핵심 시나리오:
 * <ul>
 *   <li>회원가입 정책(2회/PT1M)에서 3번째 요청 → AUTH_014 + Retry-After 헤더</li>
 *   <li>로그인 정책(2회/PT1M)에서 3번째 요청 → AUTH_014</li>
 *   <li>정책 미매칭 endpoint(/users/me) → Rate Limit 무영향 (인증 실패로만 거부)</li>
 *   <li>매 요청 응답에 X-RateLimit-Limit/Remaining 헤더 첨부</li>
 * </ul>
 *
 * <p>테스트 정책은 실 운영(3회/10분 등)보다 짧게 잡아 빠른 검증 + 다른 통합 테스트 격리.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    /**
     * AbstractIntegrationTest는 {@code ratelimit.enabled=false}로 비활성화하지만, Spring 규칙상
     * {@code @DynamicPropertySource}가 일반 test property source보다 우선한다.
     * 본 테스트는 Rate Limit 자체 동작을 검증하므로 추가 {@code @DynamicPropertySource}로
     * 활성화 + 짧은 정책을 강제 주입 (서브클래스 source가 같은 키에 대해 덮어씀).
     *
     * <p>테스트 정책은 실 운영(3회/10분 등)보다 짧게 잡아 빠른 검증 + 다른 통합 테스트 격리.
     */
    @DynamicPropertySource
    static void enableShortRateLimit(DynamicPropertyRegistry registry) {
        registry.add("ratelimit.enabled", () -> "true");
        // 회원가입: 2회/PT1M (실 운영 3회/10분보다 짧게 테스트 빠르게)
        registry.add("ratelimit.endpoints[0].id", () -> "signup");
        registry.add("ratelimit.endpoints[0].method", () -> "POST");
        registry.add("ratelimit.endpoints[0].path", () -> "/api/v1/users/auth/signup");
        registry.add("ratelimit.endpoints[0].limit", () -> "2");
        registry.add("ratelimit.endpoints[0].window", () -> "PT1M");
        // 로그인: 2회/PT1M
        registry.add("ratelimit.endpoints[1].id", () -> "user-login");
        registry.add("ratelimit.endpoints[1].method", () -> "POST");
        registry.add("ratelimit.endpoints[1].path", () -> "/api/v1/users/auth/login");
        registry.add("ratelimit.endpoints[1].limit", () -> "2");
        registry.add("ratelimit.endpoints[1].window", () -> "PT1M");
        // 신고 접수: 2회/PT1M (실 운영 10회/분보다 짧게 테스트 빠르게)
        registry.add("ratelimit.endpoints[2].id", () -> "report-create");
        registry.add("ratelimit.endpoints[2].method", () -> "POST");
        registry.add("ratelimit.endpoints[2].path", () -> "/api/v1/reports");
        registry.add("ratelimit.endpoints[2].limit", () -> "2");
        registry.add("ratelimit.endpoints[2].window", () -> "PT1M");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StringRedisTemplate redis;

    /**
     * Rate Limit 키는 ratelimit:{id}:{ip} 형태. 같은 IP(127.0.0.1)를 재사용하므로 매 테스트 후 SCAN으로 정리.
     */
    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
        ScanOptions options = ScanOptions.scanOptions().match("ratelimit:*").count(100).build();
        Set<String> keys = new HashSet<>();
        try (var cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void signup_third_request_within_window_returns_AUTH_014_with_retry_after() throws Exception {
        // 1번째, 2번째는 통과 (limit=2)
        signup("u1@example.com", "닉네임A").andExpect(status().isCreated());
        signup("u2@example.com", "닉네임B").andExpect(status().isCreated());

        // 3번째 → AUTH_014 + Retry-After 헤더 + X-RateLimit-* 헤더
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("u3@example.com", "Pa$$w0rd1!", "닉네임C"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_014"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }

    @Test
    void login_third_request_within_window_returns_AUTH_014() throws Exception {
        // 로그인 시도는 인증 실패해도 rate limit 카운트 대상 (무차별 공격 방어)
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/users/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest("nonexistent@example.com", "Wrong!Pass1"))))
                    .andExpect(status().isUnauthorized());  // AUTH_001
        }

        // 3번째 → AUTH_014 (인증 실패 AUTH_001이 아니라 rate limit이 먼저)
        mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nonexistent@example.com", "Wrong!Pass1"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_014"));
    }

    @Test
    void report_create_exceeding_limit_returns_AUTH_014() throws Exception {
        // 신고 접수는 인증 필요(미인증 시 AUTH_006). rate limit 필터는 인증 전에 카운트하므로
        // limit(2) 초과 시 AUTH_006이 아니라 AUTH_014(429)가 먼저 반환된다 (report-bombing 방어).
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());  // AUTH_006 (토큰 없음)
        }

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_014"));
    }

    @Test
    void unmatched_endpoint_is_not_rate_limited() throws Exception {
        // /api/v1/users/me는 정책 미매칭 → rate limit 영향 없음.
        // 인증 토큰 없이 호출하므로 AUTH_006 응답이지만 그건 rate limit과 무관.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_006"));
        }
    }

    @Test
    void allowed_signup_response_includes_rate_limit_headers() throws Exception {
        // 허용된 요청도 X-RateLimit-Limit/Remaining 헤더로 한도 정보 안내
        signup("anyone@example.com", "정상유저")
                .andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
    }

    private org.springframework.test.web.servlet.ResultActions signup(String email, String nickname) throws Exception {
        return mockMvc.perform(post("/api/v1/users/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new SignupRequest(email, "Pa$$w0rd1!", nickname))));
    }
}
