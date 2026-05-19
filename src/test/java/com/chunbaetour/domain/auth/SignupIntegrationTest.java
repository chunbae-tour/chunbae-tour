package com.chunbaetour.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * 회원가입 endpoint 통합 테스트 (S1).
 *
 * <p>S3 마이그레이션: {@link AbstractIntegrationTest} 상속으로 MySQL/Redis 컨테이너 + 보안 properties를 공유.
 * 기존에 SpringBootTest properties에 직접 박혀있던 Redis exclude + JWT props는 base 클래스로 이관.
 *
 * <p>본 PR(S3)의 회원가입 자체 로직은 변경 없음. 컨텍스트 구성만 base에 위임.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SignupIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    /**
     * 컨테이너는 JVM 단위로 공유되므로 테스트 간 데이터 누수 방지를 위해 본 테스트 데이터만 정리한다.
     */
    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
    }

    @Test
    void signup_with_valid_request_returns_201_and_user_data() throws Exception {
        SignupRequest request = new SignupRequest(
                "user@example.com",
                "Pa$$w0rd1!",
                "춘배유저"
        );

        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("춘배유저"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                // 응답에 비밀번호 노출 금지 확인 (보안 회귀 방지)
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void signup_with_duplicate_email_returns_409_AUTH_008() throws Exception {
        SignupRequest first = new SignupRequest(
                "duplicate@example.com", "Pa$$w0rd1!", "첫유저");
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        SignupRequest second = new SignupRequest(
                "duplicate@example.com", "Pa$$w0rd2!", "두번째유저");

        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_008"));
    }

    @Test
    void signup_with_duplicate_nickname_returns_409_AUTH_009() throws Exception {
        SignupRequest first = new SignupRequest(
                "first@example.com", "Pa$$w0rd1!", "같은닉네임");
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        SignupRequest second = new SignupRequest(
                "second@example.com", "Pa$$w0rd2!", "같은닉네임");

        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_009"));
    }

    @Test
    void signup_with_invalid_password_format_returns_400_AUTH_010() throws Exception {
        SignupRequest request = new SignupRequest(
                "user@example.com", "weakpass", "춘배유저");

        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_010"));
    }

    @Test
    void signup_with_invalid_email_format_returns_400_AUTH_011() throws Exception {
        SignupRequest request = new SignupRequest(
                "not-an-email", "Pa$$w0rd1!", "춘배유저");

        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_011"));
    }
}
