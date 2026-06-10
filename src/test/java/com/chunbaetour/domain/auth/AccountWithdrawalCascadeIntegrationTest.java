package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.common.audit.SecurityAuditLogger;
import com.chunbaetour.domain.like.entity.UserLike;
import com.chunbaetour.domain.like.repository.UserLikeRepository;
import com.chunbaetour.domain.like.type.LikeTargetType;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 회원 탈퇴 cascade / audit / 재가입 / race 통합 테스트 (Epic C S2, KAN-144).
 *
 * <p>S1({@code AccountWithdrawalIntegrationTest})은 정상 흐름 + 토큰 무효화를 검증한다. 본 클래스는 S2에서
 * 추가된 항목을 별도 그룹화:
 * <ul>
 *   <li>UserLike cascade 즉시 hard delete (ADR §2 B안)</li>
 *   <li>Wallet 보존 (ADR §1 A안)</li>
 *   <li>{@code ACCOUNT_DELETED} audit event 발행 + metadata (tokenRole/deletedLikes)</li>
 *   <li>동일 email 재가입 차단 (ADR §3 c안) — {@code AUTH_008}</li>
 *   <li>동시 탈퇴 race 가드 (ADR §5 CAS UPDATE) — 한 쪽 204, 다른 쪽 4xx</li>
 * </ul>
 *
 * <p>본 클래스가 S2 ADR ({@code docs/operations/account-withdrawal-policy.md})의 회귀 가드 역할.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountWithdrawalCascadeIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "cascade@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "캐스케이드";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserLikeRepository userLikeRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Logger auditLogbackLogger;
    private ListAppender<ILoggingEvent> auditAppender;

    @BeforeEach
    void attachAuditAppender() {
        // logback의 audit.security logger에 ListAppender 부착 — emit 이벤트 캡처.
        // 본 logger는 logback-spring.xml에서 파일/stdout JSON으로 매핑되지만 본 테스트는 메모리 캡처가 목적.
        auditLogbackLogger = (Logger) LoggerFactory.getLogger(SecurityAuditLogger.LOGGER_NAME);
        auditAppender = new ListAppender<>();
        auditAppender.start();
        auditLogbackLogger.addAppender(auditAppender);
    }

    @AfterEach
    void cleanup() {
        if (auditLogbackLogger != null && auditAppender != null) {
            auditLogbackLogger.detachAppender(auditAppender);
        }
        // 외래키 의존 순서: user_likes → wallets → users (모두 native — soft-deleted row까지 정리)
        jdbcTemplate.execute("DELETE FROM user_likes");
        jdbcTemplate.execute("DELETE FROM places");
        jdbcTemplate.execute("DELETE FROM wallets");
        jdbcTemplate.execute("DELETE FROM users");
        var refreshKeys = redis.keys("auth:refresh:*");
        if (refreshKeys != null && !refreshKeys.isEmpty()) {
            redis.delete(refreshKeys);
        }
        var blacklistKeys = redis.keys("auth:blacklist:*");
        if (blacklistKeys != null && !blacklistKeys.isEmpty()) {
            redis.delete(blacklistKeys);
        }
    }

    // ===== ADR §2 — UserLike 즉시 hard delete =====

    @Test
    void user_likes_are_hard_deleted_on_withdrawal() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        Long userId = accountRepository.findByEmail(EMAIL).orElseThrow().getId();
        Account account = accountRepository.findById(userId).orElseThrow();

        // Place 2개 + UserLike 2개 시드 — 사용자가 탈퇴해도 places는 영향 없어야 한다.
        Place placeA = placeRepository.save(samplePlace("경복궁", new BigDecimal("37.5796"), new BigDecimal("126.9770")));
        Place placeB = placeRepository.save(samplePlace("창덕궁", new BigDecimal("37.5826"), new BigDecimal("126.9911")));
        userLikeRepository.save(UserLike.of(account, LikeTargetType.PLACE, placeA.getId()));
        userLikeRepository.save(UserLike.of(account, LikeTargetType.PLACE, placeB.getId()));

        Integer likesBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_likes WHERE user_id = ?", Integer.class, userId);
        assertThat(likesBefore).isEqualTo(2);

        String accessToken = loginAccessToken(EMAIL, PASSWORD);
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // 본인 user_likes는 0이어야 함 (cascade hard delete 효과)
        Integer likesAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_likes WHERE user_id = ?", Integer.class, userId);
        assertThat(likesAfter).isZero();
        // places 자체는 영향 없음 — UserLike만 cascade, Place는 공용 데이터
        Integer placesAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM places", Integer.class);
        assertThat(placesAfter).isEqualTo(2);
    }

    // ===== ADR §1 — Wallet 보존 =====

    @Test
    void wallet_is_preserved_on_withdrawal() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        Long userId = accountRepository.findByEmail(EMAIL).orElseThrow().getId();

        // signup 흐름의 WalletEventListener가 wallet row를 자동 생성. 검증.
        Integer walletsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallets WHERE user_id = ?", Integer.class, userId);
        assertThat(walletsBefore)
                .as("signup이 WalletEventListener를 통해 wallet 자동 생성")
                .isEqualTo(1);

        String accessToken = loginAccessToken(EMAIL, PASSWORD);
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        Integer walletsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallets WHERE user_id = ?", Integer.class, userId);
        assertThat(walletsAfter)
                .as("ADR §1 — Wallet은 환불/세무 대응을 위해 탈퇴 후에도 보존")
                .isEqualTo(1);
    }

    // ===== ADR §4 — ACCOUNT_DELETED audit event =====

    @Test
    void ACCOUNT_DELETED_audit_event_is_emitted_with_tokenRole_and_deletedLikes() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        Long userId = accountRepository.findByEmail(EMAIL).orElseThrow().getId();
        Account account = accountRepository.findById(userId).orElseThrow();

        // UserLike 1건 시드 → deletedLikes metadata = "1"
        Place place = placeRepository.save(samplePlace("석굴암", new BigDecimal("35.7950"), new BigDecimal("129.3500")));
        userLikeRepository.save(UserLike.of(account, LikeTargetType.PLACE, place.getId()));

        String accessToken = loginAccessToken(EMAIL, PASSWORD);
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // afterCommit에서 emit되는 ACCOUNT_DELETED 이벤트 정확히 1건 캡처
        List<ILoggingEvent> accountDeletedEvents = auditAppender.list.stream()
                .filter(e -> "ACCOUNT_DELETED".equals(e.getMDCPropertyMap().get("audit.eventType")))
                .toList();
        assertThat(accountDeletedEvents)
                .as("ADR §5 CAS UPDATE — markAsDeleted 영향 row 1을 받은 호출자만 audit 발행 경로 진입 → 1건당 정확히 1번")
                .hasSize(1);

        var mdc = accountDeletedEvents.get(0).getMDCPropertyMap();
        assertThat(mdc).containsEntry("audit.outcome", "SUCCESS");
        assertThat(mdc).containsEntry("audit.actorId", String.valueOf(userId));
        // tokenRole — Access Token claim 기준 (DB 현재값 아님, PR #217 hyeonmin02 review 반영)
        assertThat(mdc).containsEntry("audit.meta.tokenRole", "USER");
        assertThat(mdc)
                .as("audit-log-catalog.md ACCOUNT_DELETED row의 deletedLikes 컬럼")
                .containsEntry("audit.meta.deletedLikes", "1");
    }

    // ===== ADR §3 — 동일 email 재가입 차단 (c안) =====

    @Test
    void same_email_resignup_after_withdrawal_returns_AUTH_008() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = loginAccessToken(EMAIL, PASSWORD);
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // 동일 email 재가입 시도 — Account.email UNIQUE 제약이 deletedAt과 무관하게 차단.
        // 정책 변경(partial unique index 도입) 시 본 테스트가 회귀 가드 역할 — ADR §3 갱신 트리거.
        SignupRequest request = new SignupRequest(EMAIL, PASSWORD, "재가입닉네임");
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_008"));
    }

    // ===== ADR §5 — 동시 탈퇴 race 가드 (CAS UPDATE) =====

    @Test
    void concurrent_withdrawal_allows_only_one_success_via_cas_update() throws Exception {
        // 두 스레드가 동일 access token으로 동시에 DELETE /me 호출.
        // 기대 결과: 한 요청은 204, 다른 요청은 4xx (AUTH_006 락 race 또는 AUTH_013 blacklist race —
        //          둘 다 동시성 가드 작동 시그널이라 응답 셋이 {204, 4xx}임을 검증).
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = loginAccessToken(EMAIL, PASSWORD);

        int threadCount = 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicInteger success204 = new AtomicInteger();
        AtomicInteger failure4xx = new AtomicInteger();
        AtomicInteger unexpectedErrors = new AtomicInteger();

        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        MvcResult r = mockMvc.perform(delete("/api/v1/users/me")
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                                .andReturn();
                        int sc = r.getResponse().getStatus();
                        if (sc == 204) {
                            success204.incrementAndGet();
                        } else if (sc >= 400 && sc < 500) {
                            failure4xx.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // 디버깅 편의 — 카운터 + 로그로 노출 (PR #217 hyeonmin02 🔵 review).
                        // 정상 race 시나리오는 4xx 응답이라 본 분기에 도달하지 않음. 도달 시 회귀 신호.
                        unexpectedErrors.incrementAndGet();
                        org.slf4j.LoggerFactory.getLogger(AccountWithdrawalCascadeIntegrationTest.class)
                                .error("동시 탈퇴 race 테스트에서 예외 발생 — 회귀 신호", e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown(); // 두 스레드 동시 출발
            assertThat(done.await(10, TimeUnit.SECONDS))
                    .as("동시 탈퇴 두 요청은 10초 안에 둘 다 응답해야 함 (락 데드락 회귀 가드)")
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(unexpectedErrors.get())
                .as("예상 외 예외 0 — 둘 다 정상적인 4xx/204 응답 흐름이어야 함")
                .isZero();
        assertThat(success204.get())
                .as("ADR §5 CAS UPDATE — markAsDeleted가 두 요청 중 하나만 영향 row 1로 통과시켜야 함")
                .isEqualTo(1);
        assertThat(failure4xx.get())
                .as("ADR §5 — 두 번째 요청은 4xx (AUTH_006 CAS 0 race 또는 AUTH_013 blacklist race)")
                .isEqualTo(1);

        // 추가 검증: ACCOUNT_DELETED audit도 정확히 1건만 발행됐는지 — race로 인한 중복 발행 회귀 가드
        long deletedAudits = auditAppender.list.stream()
                .filter(e -> "ACCOUNT_DELETED".equals(e.getMDCPropertyMap().get("audit.eventType")))
                .count();
        assertThat(deletedAudits)
                .as("ADR §5 — audit emit은 commit 후 1번 (afterCommit). 락 race로 중복 발행되면 회귀.")
                .isEqualTo(1);
    }

    // ===== helpers =====

    private void signup(String email, String password, String nickname) throws Exception {
        SignupRequest request = new SignupRequest(email, password, nickname);
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String loginAccessToken(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asString();
    }

    private Place samplePlace(String name, BigDecimal lat, BigDecimal lng) {
        return Place.builder()
                .name(name)
                .category(PlaceCategory.TOURIST_SPOT)
                .address("서울시 종로구")
                .lat(lat)
                .lng(lng)
                .build();
    }
}
