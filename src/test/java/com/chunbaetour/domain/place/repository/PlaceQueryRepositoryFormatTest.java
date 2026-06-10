package com.chunbaetour.domain.place.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PlaceQueryRepositoryFormatTest {

    @InjectMocks
    private PlaceQueryRepository placeQueryRepository;

    @Test
    @DisplayName("Boolean Mode 검색어 포맷팅 테스트")
    void testFormatForBooleanMode() {
        // given
        String keyword1 = "제주";
        String keyword2 = "제주 카페";
        String keyword3 = "  제주   테마파크  ";
        String keyword4 = "";
        String keyword5 = null;

        // when & then
        assertThat(placeQueryRepository.formatForBooleanMode(keyword1)).isEqualTo("+제주*");
        assertThat(placeQueryRepository.formatForBooleanMode(keyword2)).isEqualTo("+제주* +카페*");
        assertThat(placeQueryRepository.formatForBooleanMode(keyword3)).isEqualTo("+제주* +테마파크*");
        assertThat(placeQueryRepository.formatForBooleanMode(keyword4)).isEqualTo("");
        assertThat(placeQueryRepository.formatForBooleanMode(keyword5)).isNull();
    }
}
