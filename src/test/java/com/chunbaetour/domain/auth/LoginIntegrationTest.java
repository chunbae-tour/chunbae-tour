package com.chunbaetour.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-only-secret-32-bytes-min-xxxxxx",
        "jwt.access-token-ttl=PT30M",
        "jwt.refresh-token-ttl=P7D"
})
@AutoConfigureMockMvc
@Testcontainers
class LoginIntegrationTest {

    private static final String EMAIL = "loginuser@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "로그인유저";

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
    void signup_then_login_then_ping_returns_200_with_userId() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userId").isNumber());
    }

    @Test
    void login_with_wrong_password_returns_401_AUTH_001() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);

        LoginRequest wrong = new LoginRequest(EMAIL, "Wrong!Pass1");
        mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrong)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void ping_without_token_returns_401_AUTH_006() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    void ping_with_invalid_token_returns_401_AUTH_003() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
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
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asString();
    }
}
