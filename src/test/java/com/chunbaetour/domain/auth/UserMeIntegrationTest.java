package com.chunbaetour.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.PatchUserMeRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 마이페이지 본인 정보 조회 end-to-end 통합 테스트 (Epic A S1, KAN-63).
 *
 * <p>PRD AC 핵심 시나리오:
 * <ul>
 *   <li>signup → login → GET /me → 200 + 필드 검증 + 민감 필드(password) 미노출</li>
 *   <li>비인증 호출 시 AUTH_006 (RestAuthenticationEntryPoint 응답)</li>
 *   <li>응답 필드가 sa-docs/04 F-USER-001 사양과 일치 (email/nickname/role/status 등)</li>
 * </ul>
 *
 * <p>MERCHANT/ADMIN 토큰으로 /users/me 접근 시 AUTH_007 검증은 KAN-49 S5의 MultiRoleAuthIntegrationTest에서
 * 이미 커버됨 (URL 권한 매핑 검증). 본 클래스에서는 USER 흐름만 집중.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserMeIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "meuser@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "마이유저";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        walletRepository.deleteAll();
        accountRepository.deleteAll();
        var refreshKeys = redis.keys("auth:refresh:*");
        if (!refreshKeys.isEmpty()) {
            redis.delete(refreshKeys);
        }
        var blacklistKeys = redis.keys("auth:blacklist:*");
        if (!blacklistKeys.isEmpty()) {
            redis.delete(blacklistKeys);
        }
    }

    @Test
    void signup_login_then_get_me_returns_200_with_expected_fields() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.nickname").value(NICKNAME))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.language").value("ko"))
                .andExpect(jsonPath("$.data.companionScore").value(0.0))
                .andExpect(jsonPath("$.data.companionReviewCount").value(0))
                .andExpect(jsonPath("$.data.createdAt").exists())
                // 보안 회귀 방지: 민감 필드는 응답에 절대 노출되면 안 됨
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.data.suspendedUntil").doesNotExist());
    }

    @Test
    void get_me_without_token_returns_401_AUTH_006() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    void get_me_with_invalid_token_returns_401_AUTH_003() throws Exception {
        // 변조된 Bearer는 JwtAuthenticationFilter에서 verifyAccess 실패 → AUTH_003
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    // ===== KAN-127 Epic A S2 — PATCH /users/me 통합 시나리오 =====

    @Test
    void patch_me_with_all_fields_updates_and_returns_new_state() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        PatchUserMeRequest req = new PatchUserMeRequest(
                "새닉네임", "en", "https://example.com/avatar.png");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.language").value("en"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://example.com/avatar.png"))
                // 응답 포맷이 GET /me와 동일한지 회귀 가드
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        // 후속 GET /me에서도 갱신 상태 영속됨 확인
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.language").value("en"));
    }

    @Test
    void patch_me_partial_only_nickname_keeps_other_fields() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        PatchUserMeRequest req = new PatchUserMeRequest("부분변경", null, null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("부분변경"))
                .andExpect(jsonPath("$.data.language").value("ko"))  // 변경 안 됨
                // 보안 회귀 방지: PATCH 응답에도 password 미노출 (일관성 강화)
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void patch_me_with_duplicate_nickname_returns_AUTH_009() throws Exception {
        // 사용자 A 가입
        signup(EMAIL, PASSWORD, NICKNAME);
        // 사용자 B 가입 (별도 이메일/닉네임)
        String otherEmail = "other@example.com";
        String otherNickname = "다른유저";
        signup(otherEmail, PASSWORD, otherNickname);

        // A가 B 닉네임으로 변경 시도
        String accessTokenA = login(EMAIL, PASSWORD);
        PatchUserMeRequest req = new PatchUserMeRequest(otherNickname, null, null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_009"));
    }

    @Test
    void patch_me_with_own_nickname_passes_without_dup_error() throws Exception {
        // 본인 닉네임 그대로 보내도 본인 제외 중복 체크 → 통과
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        PatchUserMeRequest req = new PatchUserMeRequest(NICKNAME, "en", null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(NICKNAME))
                .andExpect(jsonPath("$.data.language").value("en"));
    }

    @Test
    void patch_me_without_token_returns_401_AUTH_006() throws Exception {
        PatchUserMeRequest req = new PatchUserMeRequest("새닉네임", null, null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    void patch_me_with_invalid_nickname_format_returns_400() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        // 1자(min=2 위반) — Bean Validation @Size 거부
        PatchUserMeRequest req = new PatchUserMeRequest("a", null, null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void patch_me_with_invalid_language_returns_400() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        // 화이트리스트 외 언어 코드
        PatchUserMeRequest req = new PatchUserMeRequest(null, "fr", null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void patch_me_with_invalid_url_returns_400() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        // http/https 스킴 아님
        PatchUserMeRequest req = new PatchUserMeRequest(null, null, "ftp://example.com/avatar.png");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void patch_me_with_all_null_fields_is_noop_and_returns_current_state() throws Exception {
        // partial update에서 모든 필드 null은 200 + 현재 상태 응답 (idempotent)
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        PatchUserMeRequest req = new PatchUserMeRequest(null, null, null);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(NICKNAME))
                .andExpect(jsonPath("$.data.language").value("ko"));
    }

    // ===== KAN-128 Epic A S3 — GET /users/me/home 통합 시나리오 =====

    @Test
    void get_me_home_returns_profile_and_wallet_balance() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        // wallet 명시 시드 (signup 후 WalletEventListener가 생성하지만 본 테스트는 직접 잔액 set)
        Long userId = accountRepository.findByEmail(EMAIL).orElseThrow().getId();
        Wallet existing = walletRepository.findByUserId(userId).orElseGet(() -> walletRepository.save(Wallet.create(userId)));
        // balance 직접 주입 — domain credit은 양수만 받으므로 reflect로 set
        ReflectionTestUtils.setField(existing, "balance", 12345L);
        walletRepository.save(existing);

        mockMvc.perform(get("/api/v1/users/me/home")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                // profile 스키마 KAN-63과 일치
                .andExpect(jsonPath("$.data.profile.userId").isNumber())
                .andExpect(jsonPath("$.data.profile.email").value(EMAIL))
                .andExpect(jsonPath("$.data.profile.nickname").value(NICKNAME))
                .andExpect(jsonPath("$.data.profile.role").value("USER"))
                .andExpect(jsonPath("$.data.profile.password").doesNotExist())
                // wallet
                .andExpect(jsonPath("$.data.wallet.balance").value(12345));
    }

    @Test
    void get_me_home_without_wallet_returns_balance_zero_fallback() throws Exception {
        // signup 직후 wallet 생성 이벤트가 아직 처리 안 됐다고 가정 — 명시적으로 wallet 삭제 후 호출
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        Long userId = accountRepository.findByEmail(EMAIL).orElseThrow().getId();
        walletRepository.findByUserId(userId).ifPresent(walletRepository::delete);

        mockMvc.perform(get("/api/v1/users/me/home")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.nickname").value(NICKNAME))
                .andExpect(jsonPath("$.data.wallet.balance").value(0));
    }

    @Test
    void get_me_home_without_token_returns_401_AUTH_006() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/home"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    // ===== KAN-130 Epic A S5 — GET /users/me/likes 통합 시나리오 =====

    @Test
    void get_me_likes_with_no_likes_returns_empty_page() throws Exception {
        // 찜 없는 사용자도 200 + 빈 페이지 응답해야 함 (404 아님)
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me/likes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void get_me_likes_respects_page_size_parameter() throws Exception {
        // 페이징 파라미터가 응답 메타데이터에 반영되는지 검증 — 시드 데이터 없이도 페이지 size 메타로 검증 가능
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me/likes?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.number").value(0));
    }

    @Test
    void get_me_likes_with_oversized_page_returns_400_INVALID_REQUEST() throws Exception {
        // PlaceLikeService.getUserLikedPlaces 내부 가드: size > 100이면 INVALID_REQUEST
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me/likes?size=200")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void get_me_likes_without_token_returns_401_AUTH_006() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/likes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    private void signup(String email, String password, String nickname) throws Exception {
        SignupRequest request = new SignupRequest(email, password, nickname);
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asString();
    }
}
