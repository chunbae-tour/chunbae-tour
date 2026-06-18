package com.chunbaetour.domain.shop.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopImage;
import com.chunbaetour.domain.shop.entity.ShopNotice;
import com.chunbaetour.domain.shop.repository.ShopImageRepository;
import com.chunbaetour.domain.shop.repository.ShopNoticeRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.storage.ShopImageKeys;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 가게 공개 API 통합 테스트 (KAN-323).
 *
 * <p>핵심 검증: 일반/비로그인 사용자가 토큰 없이 가게 대표·갤러리 이미지와 공지를 볼 수 있어야 한다.
 * <ul>
 *   <li>GET /api/v1/shops/{id} — 무인증 200, 대표 이미지(representativeImageUrl) + 갤러리(images) 임베드</li>
 *   <li>GET /api/v1/shops/{id}/notices — 무인증 200 (SecurityConfig·JwtAuthenticationFilter 공개 패턴 동기화 검증)</li>
 *   <li>SUSPENDED 가게는 공지·정보 모두 404(존재 노출 방지), CLOSED는 휴무 공지를 위해 허용</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShopPublicControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ShopRepository shopRepository;
    @Autowired private ShopImageRepository shopImageRepository;
    @Autowired private ShopNoticeRepository shopNoticeRepository;

    @AfterEach
    void cleanup() {
        shopNoticeRepository.deleteAll();
        shopImageRepository.deleteAll();
        shopRepository.deleteAll();
    }

    // ── GET /api/v1/shops/{id} — 이미지 임베드 ──────────────────────────────

    @Test
    @DisplayName("가게 공개 정보 조회 — 무인증 200 + 대표·갤러리 이미지 임베드")
    void getShopInfo_noAuth_returnsImages() throws Exception {
        Shop shop = seedShop(ShopStatus.ACTIVE);
        // 대표 1장 + 갤러리 1장 — 키는 가게 소유 prefix(shops/{id}/)여야 presign 대상
        shopImageRepository.save(ShopImage.profile(shop.getId(), ShopImageKeys.prefix(shop.getId()) + "p.jpg"));
        shopImageRepository.save(ShopImage.gallery(shop.getId(), ShopImageKeys.prefix(shop.getId()) + "g.jpg", 0));

        mockMvc.perform(get("/api/v1/shops/" + shop.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.representativeImageUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andExpect(jsonPath("$.data.images[0].type").value("GALLERY"));
    }

    @Test
    @DisplayName("가게 공개 정보 조회 — 이미지 없으면 대표 null·갤러리 빈 배열")
    void getShopInfo_noImages_returnsEmpty() throws Exception {
        Shop shop = seedShop(ShopStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/shops/" + shop.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.representativeImageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.images.length()").value(0));
    }

    // ── GET /api/v1/shops/{id}/notices — 공개 공지 ──────────────────────────

    @Test
    @DisplayName("가게 공개 공지 조회 — 무인증 200 (보안 공개 패턴 동작 검증)")
    void getShopNotices_noAuth_returns200() throws Exception {
        Shop shop = seedShop(ShopStatus.ACTIVE);
        shopNoticeRepository.save(ShopNotice.create(shop.getId(), "휴무 안내", "오늘 휴무입니다"));

        mockMvc.perform(get("/api/v1/shops/" + shop.getId() + "/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("휴무 안내"));
    }

    @Test
    @DisplayName("가게 공개 공지 조회 — CLOSED 가게도 허용 (휴무 공지)")
    void getShopNotices_closedShop_returns200() throws Exception {
        Shop shop = seedShop(ShopStatus.CLOSED);
        shopNoticeRepository.save(ShopNotice.create(shop.getId(), "폐업 안내", "그동안 감사했습니다"));

        mockMvc.perform(get("/api/v1/shops/" + shop.getId() + "/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    @DisplayName("가게 공개 공지 조회 — SUSPENDED 가게 404 SHOP_001 (공개 차단)")
    void getShopNotices_suspendedShop_returns404() throws Exception {
        Shop shop = seedShop(ShopStatus.SUSPENDED);
        shopNoticeRepository.save(ShopNotice.create(shop.getId(), "안내", "내용"));

        mockMvc.perform(get("/api/v1/shops/" + shop.getId() + "/notices"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOP_001"));
    }

    @Test
    @DisplayName("가게 공개 공지 조회 — size=101 → 400 COMMON_002 (@Max)")
    void getShopNotices_sizeOverLimit_returns400() throws Exception {
        Shop shop = seedShop(ShopStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/shops/" + shop.getId() + "/notices").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private Shop seedShop(ShopStatus status) {
        Shop shop = Shop.builder()
                .userId(9001L).applicationId(System.nanoTime())
                .shopName("공개가게").category("FOOD")
                .address("서울시 강남구").lat(new BigDecimal("37.4979000"))
                .lng(new BigDecimal("127.0276000"))
                .phone("02-0000-0000").description("공개 소개").build();
        if (status == ShopStatus.SUSPENDED) {
            shop.hide();
        } else if (status == ShopStatus.CLOSED) {
            ReflectionTestUtils.setField(shop, "status", ShopStatus.CLOSED);
        }
        return shopRepository.save(shop);
    }
}
