package com.chunbaetour.domain.search.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.search.service.IntegratedSearchService;
import com.chunbaetour.domain.search.service.PopularSearchService;
import com.chunbaetour.domain.search.service.RecentSearchService;
import com.chunbaetour.domain.search.service.SearchService;
import com.chunbaetour.domain.search.service.SuggestService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    private MockMvc mockMvc;

    @Mock private SearchService searchService;
    @Mock private PopularSearchService popularSearchService;
    @Mock private RecentSearchService recentSearchService;
    @Mock private SuggestService suggestService;
    @Mock private IntegratedSearchService integratedSearchService;

    @InjectMocks private SearchController searchController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(searchController).build();
    }

    @Test
    @DisplayName("GET /api/v1/search/places 호출 시 source 파라미터가 SearchService로 전달된다")
    void searchPlaces_PassesSourceToService() throws Exception {
        // given
        String source = "companion-place-selector";
        given(searchService.searchPlaces(eq("광장시장"), any(), any(), any(), eq(8), any(), eq(source), any()))
                .willReturn(new CursorPageResponse<>(Collections.emptyList(), null, false, 0));

        // when & then
        mockMvc.perform(get("/api/v1/search/places")
                        .param("q", "광장시장")
                        .param("size", "8")
                        .param("source", source))
                .andExpect(status().isOk());
    }
}
