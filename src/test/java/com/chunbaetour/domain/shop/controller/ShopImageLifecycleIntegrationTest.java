package com.chunbaetour.domain.shop.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopImage;
import com.chunbaetour.domain.shop.repository.ShopImageRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopImageType;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.List;
import org.assertj.core.api.Assertions;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 가게 사진 라이프사이클 통합 테스트 (KAN-315, ADR-003).
 *
 * <p>업로드(1단계 행 생성)·목록(presign)·삭제(행+객체) end-to-end + 보안(401/403)·IDOR(타 가게 imageId)·
 * PROFILE 교체(가게당 1장)·GALLERY 정렬·type 검증·SUSPENDED 차단을 고정한다. storage는 비-prod 프로파일의
 * LocalDiskShopImageStorage가 활성화되며 presign은 passthrough(url=객체키)다.
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
class ShopImageLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/merchants/me/shops";

    @Autowired private MockMvc mockMvc;
    @Autowired private ShopRepository shopRepository;
    @Autowired private ShopImageRepository shopImageRepository;
    @Autowired private TokenIssuer tokenIssuer;

    @AfterEach
    void cleanup() {
        shopImageRepository.deleteAll();
        shopRepository.deleteAll();
    }

    /** JPEG 매직바이트(FF D8 FF)로 시작하는 더미 — magic-byte 검증 통과용. */
    private MockMultipartFile jpeg(String name) {
        byte[] b = new byte[64];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        return new MockMultipartFile("file", name, "image/jpeg", b);
    }

    private Shop seedShop(Long userId) {
        return shopRepository.save(Shop.builder()
                .userId(userId).applicationId(userId)
                .shopName("사진테스트가게").category("FOOD")
                .address("서울시 강남구").phone("02-0000-0000").description("설명").build());
    }

    @Test
    @DisplayName("PROFILE 업로드 → 201 + 행 생성(type=PROFILE, isPrimary, url) + DB 1행")
    void uploadProfile_created() throws Exception {
        Long userId = 7001L;
        String token = tokenIssuer.issueAccess(userId, Role.MERCHANT, "m@test.com");
        Shop shop = seedShop(userId);

        mockMvc.perform(multipart(BASE + "/" + shop.getId() + "/images")
                        .file(jpeg("p.jpg"))
                        .param("type", "PROFILE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.type").value("PROFILE"))
                .andExpect(jsonPath("$.data.isPrimary").value(true))
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("shops/" + shop.getId() + "/")));

        List<ShopImage> rows = shopImageRepository.findByShopIdAndType(shop.getId(), ShopImageType.PROFILE);
        Assertions.assertThat(rows).hasSize(1);
    }

    @Test
    @DisplayName("PROFILE 재업로드 → 기존 교체(가게당 1장 유지, 키 변경)")
    void uploadProfile_replacesExisting() throws Exception {
        Long userId = 7002L;
        String token = tokenIssuer.issueAccess(userId, Role.MERCHANT, "m@test.com");
        Shop shop = seedShop(userId);
        String url = BASE + "/" + shop.getId() + "/images";

        mockMvc.perform(multipart(url).file(jpeg("a.jpg")).param("type", "PROFILE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated());
        String firstKey = shopImageRepository.findByShopIdAndType(shop.getId(), ShopImageType.PROFILE)
                .get(0).getObjectKey();

        mockMvc.perform(multipart(url).file(jpeg("b.jpg")).param("type", "PROFILE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated());

        List<ShopImage> rows = shopImageRepository.findByShopIdAndType(shop.getId(), ShopImageType.PROFILE);
        Assertions.assertThat(rows).hasSize(1); // 교체 — 누적 안 됨
        Assertions.assertThat(rows.get(0).getObjectKey()).isNotEqualTo(firstKey); // 새 키로 대체
    }

    @Test
    @DisplayName("GALLERY 다중 업로드 → sort_order 증가 + 목록은 PROFILE 우선 + 정렬순")
    void uploadGallery_ordersAndLists() throws Exception {
        Long userId = 7003L;
        String token = tokenIssuer.issueAccess(userId, Role.MERCHANT, "m@test.com");
        Shop shop = seedShop(userId);
        String url = BASE + "/" + shop.getId() + "/images";

        // GALLERY 2장 + PROFILE 1장
        for (String n : List.of("g1.jpg", "g2.jpg")) {
            mockMvc.perform(multipart(url).file(jpeg(n)).param("type", "GALLERY")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(multipart(url).file(jpeg("p.jpg")).param("type", "PROFILE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated());

        // 목록 — 대표(PROFILE) 먼저(type DESC), 그 다음 갤러리 sort_order 오름차순
        mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].type").value("PROFILE"))
                .andExpect(jsonPath("$.data[1].type").value("GALLERY"))
                .andExpect(jsonPath("$.data[1].sortOrder").value(0))
                .andExpect(jsonPath("$.data[2].type").value("GALLERY"))
                .andExpect(jsonPath("$.data[2].sortOrder").value(1));
    }

    @Test
    @DisplayName("DELETE 본인 사진 → 행 제거")
    void deleteImage_removesRow() throws Exception {
        Long userId = 7004L;
        String token = tokenIssuer.issueAccess(userId, Role.MERCHANT, "m@test.com");
        Shop shop = seedShop(userId);
        String url = BASE + "/" + shop.getId() + "/images";

        mockMvc.perform(multipart(url).file(jpeg("g.jpg")).param("type", "GALLERY")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated());
        Long imageId = shopImageRepository.findByShopIdOrderByTypeDescSortOrderAscIdAsc(shop.getId())
                .get(0).getId();

        mockMvc.perform(delete(url + "/" + imageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        Assertions.assertThat(shopImageRepository.findById(imageId)).isEmpty();
    }

    @Test
    @DisplayName("DELETE 타 가게 imageId(IDOR) → 404 SHOP_025")
    void deleteImage_otherShop_notFound() throws Exception {
        // 가게 A에 사진 생성, 가게 B 소유자가 A의 imageId 삭제 시도
        Long ownerA = 7005L;
        Shop shopA = seedShop(ownerA);
        ShopImage img = shopImageRepository.save(ShopImage.gallery(shopA.getId(), "shops/" + shopA.getId() + "/x.jpg", 0));

        Long ownerB = 7006L;
        String tokenB = tokenIssuer.issueAccess(ownerB, Role.MERCHANT, "b@test.com");
        Shop shopB = seedShop(ownerB);

        // B 가게 경로로 A의 imageId 삭제 시도 → 행이 B 소유가 아니라 SHOP_025
        mockMvc.perform(delete(BASE + "/" + shopB.getId() + "/images/" + img.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOP_025"));
    }

    @Test
    @DisplayName("type 파라미터 누락 → 400 (필수)")
    void upload_missingType_badRequest() throws Exception {
        Long userId = 7007L;
        String token = tokenIssuer.issueAccess(userId, Role.MERCHANT, "m@test.com");
        Shop shop = seedShop(userId);

        mockMvc.perform(multipart(BASE + "/" + shop.getId() + "/images")
                        .file(jpeg("p.jpg"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SUSPENDED 가게 업로드 → 403 SHOP_005")
    void upload_suspendedShop_forbidden() throws Exception {
        Long userId = 7008L;
        String token = tokenIssuer.issueAccess(userId, Role.MERCHANT, "m@test.com");
        Shop shop = seedShop(userId);
        ReflectionTestUtils.setField(shop, "status", ShopStatus.SUSPENDED);
        shopRepository.save(shop);

        mockMvc.perform(multipart(BASE + "/" + shop.getId() + "/images")
                        .file(jpeg("p.jpg")).param("type", "PROFILE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SHOP_005"));
    }

    @Test
    @DisplayName("토큰 없이 업로드 → 401 AUTH_006")
    void upload_noToken_unauthorized() throws Exception {
        mockMvc.perform(multipart(BASE + "/1/images").file(jpeg("p.jpg")).param("type", "PROFILE")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("USER 토큰으로 업로드 → 403 AUTH_007")
    void upload_userToken_forbidden() throws Exception {
        String userToken = tokenIssuer.issueAccess(7009L, Role.USER, "u@test.com");
        mockMvc.perform(multipart(BASE + "/1/images").file(jpeg("p.jpg")).param("type", "PROFILE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }
}
