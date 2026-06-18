package com.chunbaetour.domain.community.free.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
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

/**
 * POST /api/v1/community/posts/free/images 통합 테스트 (KAN-317).
 * 업로드 happy(키 반환·USER 권한 도달) + 보안(401) + 형식 검증(COMMUNITY_014). storage는 비-prod 로컬 디스크(passthrough).
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
class PostImageControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/community/posts/free/images";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenIssuer tokenIssuer;

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "x.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
    }

    @Test
    @DisplayName("USER 업로드 → 201 + 객체 키(posts/free/{userId}/) + 미리보기 URL")
    void upload_user_created() throws Exception {
        long userId = 7701L;
        String token = tokenIssuer.issueAccess(userId, Role.USER, "u@test.com");

        mockMvc.perform(multipart(ENDPOINT)
                        .file(jpeg())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.objectKey")
                        .value(org.hamcrest.Matchers.startsWith("posts/free/" + userId + "/")))
                .andExpect(jsonPath("$.data.previewUrl").exists());
    }

    @Test
    @DisplayName("토큰 없이 업로드 → 401 AUTH_006")
    void upload_noToken_unauthorized() throws Exception {
        mockMvc.perform(multipart(ENDPOINT)
                        .file(jpeg())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("허용 안 되는 형식(gif) → 400 COMMUNITY_014")
    void upload_badType_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(7702L, Role.USER, "u@test.com");
        MockMultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{'G', 'I', 'F', '8'});

        mockMvc.perform(multipart(ENDPOINT)
                        .file(gif)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMUNITY_014"));
    }
}
