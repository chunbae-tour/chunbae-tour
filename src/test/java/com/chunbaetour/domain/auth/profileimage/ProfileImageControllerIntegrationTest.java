package com.chunbaetour.domain.auth.profileimage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * POST /api/v1/users/me/profile-image 통합 테스트 (KAN-320).
 * 업로드 happy(키+미리보기·USER 도달) + 보안(401) + 형식(AUTH_025). 로컬 디스크 storage(passthrough).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "portone.secret=test-secret",
        "portone.webhook-secret=test-webhook-secret",
        "portone.store-id=test-store",
        "portone.base-url=http://localhost:9999",
        "portone.channel.card=test-channel-card"
})
class ProfileImageControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/users/me/profile-image";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private WalletRepository walletRepository;

    @AfterEach
    void cleanup() {
        walletRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
    }

    private String signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, "Pa$$w0rd1!", "프사유저"))))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "Pa$$w0rd1!"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    @Test
    @DisplayName("USER 업로드 → 201 + 객체 키(users/{id}/profile/) + 미리보기")
    void upload_created() throws Exception {
        String token = signupAndLogin("pi1@example.com");

        mockMvc.perform(multipart(ENDPOINT)
                        .file(jpeg())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.objectKey").value(org.hamcrest.Matchers.matchesPattern(
                        "users/\\d+/profile/[0-9a-fA-F\\-]{36}\\.jpg")))
                .andExpect(jsonPath("$.data.previewUrl").exists());
    }

    @Test
    @DisplayName("토큰 없이 → 401 AUTH_006")
    void upload_noToken_401() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(jpeg()).contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("형식 위반(gif) → 400 AUTH_025")
    void upload_badType_400() throws Exception {
        String token = signupAndLogin("pi2@example.com");
        MockMultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{'G', 'I', 'F', '8'});

        mockMvc.perform(multipart(ENDPOINT)
                        .file(gif)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_025"));
    }
}
