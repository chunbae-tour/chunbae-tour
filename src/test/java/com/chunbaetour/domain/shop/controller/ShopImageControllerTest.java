package com.chunbaetour.domain.shop.controller;

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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST /api/v1/merchants/me/shops/{shopId}/images 권한 및 파라미터 통합 테스트.
 * 모든 케이스가 소유권 확인(SHOP_NOT_FOUND) 또는 Security 단계에서 종료되어 storage(S3/LocalDisk)까지 도달하지 않는다.
 * 실제 storage 동작(업로드·키 반환)은 단위 테스트(ShopImageServiceTest / S3ShopImageStorageTest)가 검증한다.
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
class ShopImageControllerTest extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/merchants/me/shops/1/images";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenIssuer tokenIssuer;

    private MockMultipartFile validFile() {
        return new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[1024]);
    }

    @Test
    @DisplayName("비인증 접근 시 401 AUTH_006")
    void upload_unauthenticated_returns401() throws Exception {
        mockMvc.perform(multipart(ENDPOINT)
                        .file(validFile())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("USER 역할 접근 시 403 AUTH_007")
    void upload_userRole_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");
        mockMvc.perform(multipart(ENDPOINT)
                        .file(validFile())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("MERCHANT 토큰 + 유효 파일 → Security 통과, 존재하지 않는 shopId → 404 SHOP_001")
    void upload_merchant_securityPassesServiceChecks() throws Exception {
        // shopId=1이 DB에 없으므로 Security 통과 후 소유권 검증에서 SHOP_NOT_FOUND 반환
        String token = tokenIssuer.issueAccess(9001L, Role.MERCHANT, "merchant@test.com");
        mockMvc.perform(multipart(ENDPOINT)
                        .file(validFile())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOP_001"));
    }

    @Test
    @DisplayName("GIF 파일 + 존재하지 않는 shopId → 소유권 검증이 파일 검증보다 먼저 실행되므로 SHOP_001")
    void upload_merchant_gifFile_shopNotFound_ownershipCheckedFirst() throws Exception {
        // 서비스 실행 순서: 소유권 확인(1) → 파일 검증(2). shopId=1이 DB에 없으므로 파일 타입 무관하게 SHOP_001 반환.
        // GIF 거부 자체는 ShopImageServiceTest에서 단위 테스트로 검증.
        String token = tokenIssuer.issueAccess(9001L, Role.MERCHANT, "merchant@test.com");
        MockMultipartFile gifFile = new MockMultipartFile("file", "anim.gif", "image/gif", new byte[512]);
        mockMvc.perform(multipart(ENDPOINT)
                        .file(gifFile)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOP_001"));
    }
}
