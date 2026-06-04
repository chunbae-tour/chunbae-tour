package com.chunbaetour.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GET /api/v1/places 관광지 공개 목록 API 통합 테스트 (KAN-218)
 *
 * <p>검증 시나리오:
 * <ul>
 *   <li>비로그인 접근 가능 (토큰 없이 200)</li>
 *   <li>ACTIVE만 노출, HIDDEN/DELETED 제외</li>
 *   <li>rating DESC, id DESC 정렬 보장</li>
 *   <li>카테고리 필터 정상 동작</li>
 *   <li>복합 커서(cursor + cursorRating) 페이징 — 중복/누락 없음</li>
 *   <li>cursor만 단독 전달 시 400</li>
 *   <li>size > 50 시 400</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlaceListControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlaceRepository placeRepository;

    @AfterEach
    void cleanup() {
        placeRepository.deleteAll();
    }

    // ── 1. 비로그인 허용 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/places → 비로그인(토큰 없음)으로 200 반환")
    void getPlaceList_noAuth_returns200() throws Exception {
        savePlace("성산일출봉", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE, 4.8f, "제주특별자치도 서귀포시");

        mockMvc.perform(get("/api/v1/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    // ── 2. ACTIVE만 노출 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("HIDDEN/DELETED 관광지는 목록에서 제외되고 ACTIVE만 반환")
    void getPlaceList_onlyActiveReturned() throws Exception {
        savePlace("성산일출봉", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE, 4.8f, "서귀포시");
        savePlace("숨겨진 곳", PlaceCategory.TOURIST_SPOT, PlaceStatus.HIDDEN, 4.5f, "서귀포시");
        savePlace("삭제된 곳", PlaceCategory.TOURIST_SPOT, PlaceStatus.DELETED, 4.3f, "서귀포시");

        mockMvc.perform(get("/api/v1/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("성산일출봉"));
    }

    // ── 3. 정렬: rating DESC, id DESC ────────────────────────────────────────

    @Test
    @DisplayName("rating DESC, id DESC 정렬 — 높은 평점이 먼저, 동일 평점이면 높은 id가 먼저")
    void getPlaceList_sortedByRatingDescIdDesc() throws Exception {
        // 평점 순서: 4.9 > 4.7 > 4.7 (id 높은 게 먼저)
        Place low  = savePlace("C관광지", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE, 4.7f, "제주시");
        Place high = savePlace("B관광지", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE, 4.7f, "제주시"); // id가 더 큼
        Place top  = savePlace("A관광지", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE, 4.9f, "제주시"); // 평점 최고

        MvcResult result = mockMvc.perform(get("/api/v1/places"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("items");

        // 첫 번째: 평점 4.9인 top, 두 번째: 동일 4.7 중 id 높은 high, 세 번째: low
        assertThat(items.get(0).get("name").asString()).isEqualTo("A관광지");
        assertThat(items.get(1).get("name").asString()).isEqualTo("B관광지");
        assertThat(items.get(2).get("name").asString()).isEqualTo("C관광지");
    }

    // ── 4. 카테고리 필터 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("category=TOURIST_SPOT 필터 → 해당 카테고리만 반환")
    void getPlaceList_categoryFilter() throws Exception {
        savePlace("성산일출봉", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE, 4.8f, "서귀포시");
        savePlace("제주동문시장", PlaceCategory.TRADITIONAL_MARKET, PlaceStatus.ACTIVE, 4.5f, "제주시");

        mockMvc.perform(get("/api/v1/places").param("category", "TOURIST_SPOT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].category").value("TOURIST_SPOT"));
    }

    // ── 5. 복합 커서 페이징 — 중복/누락 없음 ─────────────────────────────────

    @Test
    @DisplayName("복합 커서 페이징 — 2페이지 조합 시 전체 데이터 중복·누락 없음")
    void getPlaceList_cursorPaging_noDuplicateOrMissing() throws Exception {
        // 평점이 서로 다른 4개 관광지 저장 (rating DESC, id DESC 정렬 기준)
        savePlace("D관광지", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE, 4.5f, "제주시");
        savePlace("C관광지", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE, 4.7f, "제주시");
        savePlace("B관광지", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE, 4.8f, "제주시");
        savePlace("A관광지", PlaceCategory.TOURIST_SPOT, PlaceStatus.ACTIVE, 4.9f, "제주시");

        // 첫 페이지 (size=2)
        MvcResult firstResult = mockMvc.perform(get("/api/v1/places").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.nextCursorId").isNumber())
                .andExpect(jsonPath("$.data.nextCursorRating").isNumber())
                .andReturn();

        JsonNode firstBody = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("data");
        String nextCursorId = firstBody.get("nextCursorId").asString();
        String nextCursorRating = firstBody.get("nextCursorRating").asString();

        // 첫 페이지 아이템 이름 수집
        List<String> firstPageNames = List.of(
                firstBody.get("items").get(0).get("name").asString(),
                firstBody.get("items").get(1).get("name").asString()
        );

        // 두 번째 페이지 (복합 커서 전달)
        MvcResult secondResult = mockMvc.perform(get("/api/v1/places")
                        .param("size", "2")
                        .param("cursor", nextCursorId)
                        .param("cursorRating", nextCursorRating))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andReturn();

        JsonNode secondBody = objectMapper.readTree(secondResult.getResponse().getContentAsString()).get("data");
        List<String> secondPageNames = List.of(
                secondBody.get("items").get(0).get("name").asString(),
                secondBody.get("items").get(1).get("name").asString()
        );

        // 중복 없음
        assertThat(firstPageNames).doesNotContainAnyElementsOf(secondPageNames);
        // 누락 없음 (합치면 4개)
        assertThat(firstPageNames.size() + secondPageNames.size()).isEqualTo(4);
    }

    // ── 6. cursor 단독 전달 시 400 ────────────────────────────────────────────

    @Test
    @DisplayName("cursor만 단독 전달 (cursorRating 없음) → 400 Bad Request")
    void getPlaceList_cursorWithoutRating_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/places").param("cursor", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cursorRating만 단독 전달 (cursor 없음) → 400 Bad Request")
    void getPlaceList_ratingWithoutCursor_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/places").param("cursorRating", "4.5"))
                .andExpect(status().isBadRequest());
    }

    // ── 7. size 범위 초과 시 400 ──────────────────────────────────────────────

    @Test
    @DisplayName("size=51 (최대 50 초과) → 400 Bad Request")
    void getPlaceList_sizeTooLarge_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/places").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("size=0 (최소 1 미만) → 400 Bad Request")
    void getPlaceList_sizeTooSmall_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/places").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private Place savePlace(String name, PlaceCategory category, PlaceStatus status,
                             float rating, String address) {
        Place place = Place.builder()
                .name(name)
                .category(category)
                .description("설명")
                .address(address)
                .lat(new BigDecimal("33.4500"))
                .lng(new BigDecimal("126.9300"))
                .operatingHours("09:00-18:00")
                .build();
        if (status == PlaceStatus.HIDDEN) {
            place.hide();
        } else if (status == PlaceStatus.DELETED) {
            place.delete();
        }
        Place saved = placeRepository.save(place);
        // rating은 recalculateRating으로 강제 설정 (리뷰 데이터 없이 테스트)
        saved.recalculateRating(rating, 1);
        return placeRepository.save(saved);
    }
}
