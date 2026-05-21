package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.search.dto.response.SearchPlaceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private PlaceQueryRepository placeQueryRepository;

    @Mock
    private PopularSearchService popularSearchService;

    @InjectMocks
    private SearchService searchService;

    @Test
    @DisplayName("검색어가 비어있을 경우 예외를 던진다 (빈/공백 q 예외)")
    void searchPlaces_ThrowsException_WhenKeywordIsEmpty() {
        // given
        String emptyKeyword = "   ";

        // when & then
        assertThatThrownBy(() -> searchService.searchPlaces(emptyKeyword, null, null, null, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SEARCH_KEYWORD_TOO_SHORT.getMessage());
    }

    @Test
    @DisplayName("검색어가 51자 이상일 경우 예외를 던진다 (51자 초과 예외)")
    void searchPlaces_ThrowsException_WhenKeywordIsTooLong() {
        // given
        String longKeyword = "a".repeat(51);

        // when & then
        assertThatThrownBy(() -> searchService.searchPlaces(longKeyword, null, null, null, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SEARCH_KEYWORD_TOO_LONG.getMessage());
    }

    @Test
    @DisplayName("정상 조회 시 cursor == null 일 때만 인기 검색어가 증가한다")
    void searchPlaces_IncrementsPopularSearch_OnlyWhenCursorIsNull() {
        // given
        String keyword = "맛집";
        int size = 10;
        List<SearchPlaceResponse> mockResult = new ArrayList<>();
        mockResult.add(new SearchPlaceResponse(1L, "맛집1", PlaceCategory.TOURIST_SPOT, "주소", "url", 4.5f, 10));
        
        when(placeQueryRepository.searchByKeyword(keyword, null, null, null, size)).thenReturn(mockResult);

        // when (cursor == null)
        searchService.searchPlaces(keyword, null, null, null, size);

        // then
        verify(popularSearchService).incrementSearchCount(keyword);
    }

    @Test
    @DisplayName("정상 조회 시 cursor != null 이면 인기 검색어가 증가하지 않는다")
    void searchPlaces_DoesNotIncrementPopularSearch_WhenCursorIsNotNull() {
        // given
        String keyword = "맛집";
        int size = 10;
        Long cursorId = 5L;
        List<SearchPlaceResponse> mockResult = new ArrayList<>();
        mockResult.add(new SearchPlaceResponse(1L, "맛집1", PlaceCategory.TOURIST_SPOT, "주소", "url", 4.5f, 10));
        
        when(placeQueryRepository.searchByKeyword(keyword, null, null, cursorId, size)).thenReturn(mockResult);

        // when (cursor != null)
        searchService.searchPlaces(keyword, null, null, cursorId, size);

        // then
        verify(popularSearchService, never()).incrementSearchCount(anyString());
    }

    @Test
    @DisplayName("hasNext 및 nextCursor가 올바르게 계산된다 (size+1 기반)")
    void searchPlaces_CalculatesHasNextAndNextCursorCorrectly() {
        // given
        String keyword = "맛집";
        int size = 2; // 요청 size 2
        
        // size + 1 인 3개가 리턴되었다고 가정 (hasNext = true)
        List<SearchPlaceResponse> mockResult = new ArrayList<>();
        mockResult.add(new SearchPlaceResponse(10L, "맛집10", PlaceCategory.TOURIST_SPOT, "주소", "url", 4.5f, 10));
        mockResult.add(new SearchPlaceResponse(9L, "맛집9", PlaceCategory.TOURIST_SPOT, "주소", "url", 4.5f, 10));
        mockResult.add(new SearchPlaceResponse(8L, "맛집8", PlaceCategory.TOURIST_SPOT, "주소", "url", 4.5f, 10));
        
        when(placeQueryRepository.searchByKeyword(keyword, null, null, null, size)).thenReturn(mockResult);

        // when
        CursorPageResponse<SearchPlaceResponse> response = searchService.searchPlaces(keyword, null, null, null, size);

        // then
        assertThat(response.hasNext()).isTrue();
        assertThat(response.content()).hasSize(2); // 3개 중 마지막 1개는 잘려나감
        assertThat(response.nextCursor()).isEqualTo("9"); // index 1 (마지막 아이템)의 ID
        assertThat(response.size()).isEqualTo(2); // 잘려나간 후 리스트 사이즈
    }
}
