package com.chunbaetour.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.dto.SignupRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-only-secret-32-bytes-min-xxxxxx",
        "jwt.access-token-ttl=PT30M",
        "jwt.refresh-token-ttl=P7D"
})
@AutoConfigureMockMvc
@Testcontainers
class SignupIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

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
