package com.chunbaetour.domain.search.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.search.repository.SearchQueryRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegratedSearchServiceTest {

    @Mock
    private SearchQueryRepository searchQueryRepository;

    @Mock
    private PopularSearchService popularSearchService;

    @InjectMocks
    private IntegratedSearchService integratedSearchService;

    @Test
    @DisplayName("integrated search increments popular keyword on trackable first page result")
    void searchIntegrated_IncrementsPopularSearch_WhenTrackableFirstPageHasResults() {
        // given
        String keyword = "market";
        Place place = mock(Place.class);
        when(place.getId()).thenReturn(1L);
        when(place.getName()).thenReturn(keyword);
        when(searchQueryRepository.searchPlaces(keyword)).thenReturn(List.of(place));

        // when
        integratedSearchService.searchIntegrated(keyword, "PLACE", null, 10, "127.0.0.1", true, null);

        // then
        verify(popularSearchService).incrementSearchCount(keyword, "127.0.0.1");
    }

    @Test
    @DisplayName("integrated search skips popular keyword when track is false")
    void searchIntegrated_DoesNotIncrementPopularSearch_WhenTrackIsFalse() {
        // given
        String keyword = "market";
        Place place = mock(Place.class);
        when(place.getId()).thenReturn(1L);
        when(place.getName()).thenReturn(keyword);
        when(searchQueryRepository.searchPlaces(keyword)).thenReturn(List.of(place));

        // when
        integratedSearchService.searchIntegrated(keyword, "PLACE", null, 10, "127.0.0.1", false, null);

        // then
        verify(popularSearchService, never()).incrementSearchCount(keyword, "127.0.0.1");
    }

    @Test
    @DisplayName("integrated search skips popular keyword for non-trackable source")
    void searchIntegrated_DoesNotIncrementPopularSearch_WhenSourceIsCommunityPlaceSelector() {
        // given
        String keyword = "market";
        Place place = mock(Place.class);
        when(place.getId()).thenReturn(1L);
        when(place.getName()).thenReturn(keyword);
        when(searchQueryRepository.searchPlaces(keyword)).thenReturn(List.of(place));

        // when
        integratedSearchService.searchIntegrated(
                keyword, "PLACE", null, 10, "127.0.0.1", true, "community-place-selector");

        // then
        verify(popularSearchService, never()).incrementSearchCount(keyword, "127.0.0.1");
    }
}
