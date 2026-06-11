package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalQueryRepository;
import com.chunbaetour.domain.festival.type.FestivalProgressStatus;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.search.dto.response.SearchFestivalResponse;
import com.chunbaetour.domain.search.dto.response.SearchPlaceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private FestivalQueryRepository festivalQueryRepository;

    @Mock
    private PopularSearchService popularSearchService;

    @Spy
    private java.time.Clock clock = java.time.Clock.systemDefaultZone();

    @InjectMocks
    private SearchService searchService;

    @Test
    @DisplayName("검색어가 비어있을 경우 예외를 던진다 (빈/공백 q 예외)")
    void searchPlaces_ThrowsException_WhenKeywordIsEmpty() {
        // given
        String emptyKeyword = "   ";

        // when & then
        assertThatThrownBy(() -> searchService.searchPlaces(emptyKeyword, null, null, null, 10, "127.0.0.1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SEARCH_KEYWORD_TOO_SHORT.getMessage());
    }

    @Test
    @DisplayName("검색어가 51자 이상일 경우 예외를 던진다 (51자 초과 예외)")
    void searchPlaces_ThrowsException_WhenKeywordIsTooLong() {
        // given
        String longKeyword = "a".repeat(51);

        // when & then
        assertThatThrownBy(() -> searchService.searchPlaces(longKeyword, null, null, null, 10, "127.0.0.1", null))
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
        searchService.searchPlaces(keyword, null, null, null, size, "127.0.0.1", null);

        // then
        verify(popularSearchService).incrementSearchCount(keyword, "127.0.0.1");
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
        searchService.searchPlaces(keyword, null, null, cursorId, size, "127.0.0.1", null);

        // then
        verify(popularSearchService, never()).incrementSearchCount(anyString(), anyString());
    }

    @Test
    @DisplayName("source가 community-place-selector이면 관광지 검색 결과가 있어도 인기 검색어를 증가시키지 않는다")
    void searchPlaces_DoesNotIncrementPopularSearch_WhenSourceIsCommunityPlaceSelector() {
        // given
        String keyword = "제주";
        int size = 10;
        when(placeQueryRepository.searchByKeyword(keyword, null, null, null, size))
                .thenReturn(List.of(new SearchPlaceResponse(1L, "제주", PlaceCategory.TOURIST_SPOT, "주소", "url", 4.5f, 1)));

        // when
        searchService.searchPlaces(keyword, null, null, null, size, "127.0.0.1", "community-place-selector");

        // then
        verify(popularSearchService, never()).incrementSearchCount(anyString(), anyString());
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
        CursorPageResponse<SearchPlaceResponse> response = searchService.searchPlaces(keyword, null, null, null, size, "127.0.0.1", null);

        // then
        assertThat(response.hasNext()).isTrue();
        assertThat(response.content()).hasSize(2); // 3개 중 마지막 1개는 잘려나감
        assertThat(response.nextCursor()).isEqualTo("9"); // index 1 (마지막 아이템)의 ID
        assertThat(response.size()).isEqualTo(2); // 잘려나간 후 리스트 사이즈
    }

    @Test
    @DisplayName("축제 검색 시 keyword가 null이어도 정상 동작하며 인기검색어는 증가하지 않는다")
    void searchFestivals_WorksWithoutKeyword_AndDoesNotIncrementPopularSearch() {
        // given
        int size = 10;
        List<Festival> mockResult = new ArrayList<>();
        Festival festival = Festival.create("축제1", "설명", "서울", "주소",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1),
                "http://url.com", null, FestivalStatus.ACTIVE);
        // 리플렉션으로 ID 세팅
        org.springframework.test.util.ReflectionTestUtils.setField(festival, "id", 1L);
        mockResult.add(festival);
        
        when(festivalQueryRepository.searchFestivals(null, null, null, null, null, size)).thenReturn(mockResult);

        // when
        CursorPageResponse<SearchFestivalResponse> response = searchService.searchFestivals(null, null, null, null, null, size, "127.0.0.1", null);

        // then
        assertThat(response.content()).hasSize(1);
        verify(popularSearchService, never()).incrementSearchCount(any(), anyString());
    }

    @Test
    @DisplayName("축제 검색 시 progressStatus가 오늘 날짜 기준으로 재계산된다")
    void searchFestivals_RecalculatesProgressStatusCorrectly() {
        // given
        String keyword = "축제";
        int size = 10;
        List<Festival> mockResult = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        Festival pastFestival = Festival.create("지난 축제", "설명", "서울", "주소",
                today.minusDays(10), today.minusDays(5), "http://url.com", null, FestivalStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(pastFestival, "id", 3L);
        
        Festival ongoingFestival = Festival.create("진행중 축제", "설명", "서울", "주소",
                today.minusDays(1), today.plusDays(5), "http://url.com", null, FestivalStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(ongoingFestival, "id", 2L);
        
        Festival futureFestival = Festival.create("예정 축제", "설명", "서울", "주소",
                today.plusDays(5), today.plusDays(10), "http://url.com", null, FestivalStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(futureFestival, "id", 1L);

        mockResult.add(pastFestival);
        mockResult.add(ongoingFestival);
        mockResult.add(futureFestival);
        
        when(festivalQueryRepository.searchFestivals(keyword, null, null, null, null, size)).thenReturn(mockResult);

        // when
        CursorPageResponse<SearchFestivalResponse> response = searchService.searchFestivals(keyword, null, null, null, null, size, "127.0.0.1", null);

        // then
        // 결과는 mockResult에 넣은 순서 (3, 2, 1 - ID 내림차순)대로 반환됨을 명시적으로 검증
        assertThat(response.content().get(0).festivalId()).isEqualTo(3L);
        assertThat(response.content().get(0).progressStatus()).isEqualTo(FestivalProgressStatus.ENDED);
        
        assertThat(response.content().get(1).festivalId()).isEqualTo(2L);
        assertThat(response.content().get(1).progressStatus()).isEqualTo(FestivalProgressStatus.IN_PROGRESS);
        
        assertThat(response.content().get(2).festivalId()).isEqualTo(1L);
        assertThat(response.content().get(2).progressStatus()).isEqualTo(FestivalProgressStatus.UPCOMING);

        // [Fix 3] keyword="축제", 결과 있음, cursorId=null → incrementSearchCount 호출 검증 누락 수정
        verify(popularSearchService).incrementSearchCount("축제", "127.0.0.1");
    }

    @Test
    @DisplayName("축제 검색 시 keyword가 51자 이상이면 예외를 던진다")
    void searchFestivals_ThrowsException_WhenKeywordIsTooLong() {
        // given
        // [Fix 4] 관광지 검색(searchPlaces)과 별도 경로인 축제 검색(searchFestivals)의
        // keyword 50자 초과 검증도 독립적으로 커버해야 한다.
        String longKeyword = "가".repeat(51);

        // when & then
        assertThatThrownBy(() -> searchService.searchFestivals(longKeyword, null, null, null, null, 10, "127.0.0.1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SEARCH_KEYWORD_TOO_LONG.getMessage());
    }

    @Test
    @DisplayName("축제 검색 시 startDate만 입력해도 정상 동작한다 (반범위 날짜)")
    void searchFestivals_WorksWithStartDateOnly() {
        // given
        // [Fix 5a] dateBetween의 startDate only 분기(festival.endDate >= startDate) 미검증 케이스
        LocalDate startDate = LocalDate.now();
        int size = 10;
        List<Festival> mockResult = new ArrayList<>();
        Festival festival = Festival.create("축제1", "설명", "서울", "주소",
                startDate, startDate.plusDays(5), "http://url.com", null, FestivalStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(festival, "id", 1L);
        mockResult.add(festival);

        when(festivalQueryRepository.searchFestivals(null, startDate, null, null, null, size)).thenReturn(mockResult);

        // when
        CursorPageResponse<SearchFestivalResponse> response =
                searchService.searchFestivals(null, startDate, null, null, null, size, "127.0.0.1", null);

        // then
        assertThat(response.content()).hasSize(1);
        // keyword null이므로 인기 검색어 집계 없음
        verify(popularSearchService, never()).incrementSearchCount(any(), anyString());
    }

    @Test
    @DisplayName("축제 검색 시 endDate만 입력해도 정상 동작한다 (반범위 날짜)")
    void searchFestivals_WorksWithEndDateOnly() {
        // given
        // [Fix 5b] dateBetween의 endDate only 분기(festival.startDate <= endDate) 미검증 케이스
        LocalDate endDate = LocalDate.now().plusDays(10);
        int size = 10;
        List<Festival> mockResult = new ArrayList<>();
        Festival festival = Festival.create("축제2", "설명", "부산", "주소",
                endDate.minusDays(3), endDate, "http://url.com", null, FestivalStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(festival, "id", 2L);
        mockResult.add(festival);

        when(festivalQueryRepository.searchFestivals(null, null, endDate, null, null, size)).thenReturn(mockResult);

        // when
        CursorPageResponse<SearchFestivalResponse> response =
                searchService.searchFestivals(null, null, endDate, null, null, size, "127.0.0.1", null);

        // then
        assertThat(response.content()).hasSize(1);
        // keyword null이므로 인기 검색어 집계 없음
        verify(popularSearchService, never()).incrementSearchCount(any(), anyString());
    }

    @Test
    @DisplayName("source가 community-place-selector이면 축제 검색 결과가 있어도 인기 검색어를 증가시키지 않는다")
    void searchFestivals_DoesNotIncrementPopularSearch_WhenSourceIsCommunityPlaceSelector() {
        // given
        String keyword = "축제";
        int size = 10;
        Festival mockFestival = Festival.create("축제", "설명", "서울", "주소",
                LocalDate.now(), LocalDate.now().plusDays(5), "http://url.com", null, FestivalStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(mockFestival, "id", 1L);
        when(festivalQueryRepository.searchFestivals(keyword, null, null, null, null, size))
                .thenReturn(List.of(mockFestival));

        // when
        searchService.searchFestivals(keyword, null, null, null, null, size, "127.0.0.1", "community-place-selector");

        // then
        verify(popularSearchService, never()).incrementSearchCount(anyString(), anyString());
    }
}
