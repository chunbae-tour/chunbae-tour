package com.chunbaetour.domain.store.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class UserItemIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "user-item@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "보유아이템";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StringRedisTemplate redis;

    /**
     * 각 테스트가 Redis와 DB의 이전 상태에 영향을 받지 않도록 정리한다.
     */
    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
        deleteByPrefix("auth:refresh:*");
        deleteByPrefix("auth:blacklist:*");
    }

    @Test
    @DisplayName("내 보유 아이템 조회 — 미인증 요청은 AUTH_006")
    void getMyItems_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("내 보유 아이템 조회 — size 상한 초과는 COMMON_002")
    void getMyItems_sizeOverMax_returns400() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me/items?size=101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    /**
     * 테스트에 필요한 사용자를 회원가입시킨다.
     *
     * @param email 회원가입 이메일
     * @param password 회원가입 비밀번호
     * @param nickname 회원가입 닉네임
     */
    private void signup(String email, String password, String nickname) throws Exception {
        SignupRequest request = new SignupRequest(email, password, nickname);
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    /**
     * 로그인 요청을 보내고 access token을 추출한다.
     *
     * @param email 로그인에 사용할 이메일
     * @param password 로그인에 사용할 비밀번호
     * @return 인증된 사용자의 access token
     */
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

    /**
     * Redis에서 접두사가 일치하는 키를 모두 수집한 뒤 일괄 삭제한다.
     *
     * <p>SCAN으로 키를 모으는 이유는 cleanup 중 Redis를 장시간 잠그는 KEYS 명령을 피하고,
     * cursor 순회와 삭제를 분리해 테스트 격리를 안정적으로 유지하기 위함이다.
     *
     * @param pattern 삭제할 키의 Redis pattern
     */
    private void deleteByPrefix(String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build();
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
