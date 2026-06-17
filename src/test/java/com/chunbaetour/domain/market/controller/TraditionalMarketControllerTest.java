package com.chunbaetour.domain.market.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.like.repository.UserLikeRepository;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GET /api/v1/traditional-markets/nearby 컨트롤러 테스트.
 * 공개 API 인증 없이 200, 파라미터 유효성, 응답 JSON 구조 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TraditionalMarketControllerTest extends AbstractIntegrationTest {

    private static final String NEARBY_ENDPOINT = "/api/v1/traditional-markets/nearby";
    private static final String MARKETS_ENDPOINT = "/api/v1/traditional-markets";
    private static final String EMAIL = "market-detail-user@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "시장상세유저";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TraditionalMarketRepository marketRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private UserLikeRepository userLikeRepository;
    @Autowired private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        userLikeRepository.deleteAll();
        marketRepository.deleteAll();
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
    @DisplayName("nearby — 토큰 없이 빈 목록 200 (공개 API 비인증 허용 검증)")
    void nearby_withoutToken_returns200() throws Exception {
        mockMvc.perform(get(NEARBY_ENDPOINT)
                        .param("lat", "37.5665")
                        .param("lng", "126.9780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.markets").isArray())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("nearby — lat 누락 시 400")
    void nearby_missingLat_returns400() throws Exception {
        mockMvc.perform(get(NEARBY_ENDPOINT)
                        .param("lng", "126.9780"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("nearby — lat 범위 초과(91) 시 400 COMMON_002")
    void nearby_latOutOfRange_returns400() throws Exception {
        mockMvc.perform(get(NEARBY_ENDPOINT)
                        .param("lat", "91")
                        .param("lng", "126.9780"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("nearby — radius 최솟값 미만(99) 시 400 COMMON_002")
    void nearby_radiusTooSmall_returns400() throws Exception {
        mockMvc.perform(get(NEARBY_ENDPOINT)
                        .param("lat", "37.5665")
                        .param("lng", "126.9780")
                        .param("radius", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("nearby — size 최댓값 초과(101) 시 400 COMMON_002")
    void nearby_sizeTooLarge_returns400() throws Exception {
        mockMvc.perform(get(NEARBY_ENDPOINT)
                        .param("lat", "37.5665")
                        .param("lng", "126.9780")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("detail — 토큰 없이 전통시장 상세 200 + isLiked=false")
    void detail_withoutToken_returns200() throws Exception {
        TraditionalMarket market = seedMarket();

        mockMvc.perform(get(MARKETS_ENDPOINT + "/{marketId}", market.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.marketId").value(market.getId()))
                .andExpect(jsonPath("$.data.name").value("광장시장"))
                .andExpect(jsonPath("$.data.address").value("서울특별시 종로구 창경궁로 88"))
                .andExpect(jsonPath("$.data.lat").value(37.5701))
                .andExpect(jsonPath("$.data.lng").value(126.9997))
                .andExpect(jsonPath("$.data.marketType").value("상설장"))
                .andExpect(jsonPath("$.data.phoneNumber").value("02-123-4567"))
                .andExpect(jsonPath("$.data.homepageUrl").value("https://example.com"))
                .andExpect(jsonPath("$.data.establishYear").value(1905))
                .andExpect(jsonPath("$.data.sido").value("서울특별시"))
                .andExpect(jsonPath("$.data.sigungu").value("종로구"))
                .andExpect(jsonPath("$.data.region").value("서울특별시 종로구"))
                .andExpect(jsonPath("$.data.targetType").value("MARKET"))
                .andExpect(jsonPath("$.data.isLiked").value(false));
    }

    @Test
    @DisplayName("detail — 로그인 사용자가 찜한 전통시장은 isLiked=true")
    void detail_withLikedUser_returnsIsLikedTrue() throws Exception {
        TraditionalMarket market = seedMarket();
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        mockMvc.perform(post(MARKETS_ENDPOINT + "/{marketId}/like", market.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(MARKETS_ENDPOINT + "/{marketId}", market.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.marketId").value(market.getId()))
                .andExpect(jsonPath("$.data.isLiked").value(true));
    }

    @Test
    @DisplayName("detail — 미존재 전통시장 404 MARKET_NOT_FOUND")
    void detail_notFound_returns404() throws Exception {
        mockMvc.perform(get(MARKETS_ENDPOINT + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_002"));
    }

    @Test
    @DisplayName("detail — marketId=0이면 400 COMMON_002")
    void detail_nonPositiveId_returns400() throws Exception {
        mockMvc.perform(get(MARKETS_ENDPOINT + "/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    private TraditionalMarket seedMarket() {
        return marketRepository.save(TraditionalMarket.builder()
                .name("광장시장")
                .address("서울특별시 종로구 창경궁로 88")
                .lat(new BigDecimal("37.5701000"))
                .lng(new BigDecimal("126.9997000"))
                .marketType("상설장")
                .phoneNumber("02-123-4567")
                .homepageUrl("https://example.com")
                .establishYear(1905)
                .sido("서울특별시")
                .sigungu("종로구")
                .build());
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
