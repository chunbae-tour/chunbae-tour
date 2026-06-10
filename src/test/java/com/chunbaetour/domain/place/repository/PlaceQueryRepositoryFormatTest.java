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
    @DisplayName("Boolean Mode 검색어 포맷팅 테스트 - 정규식 기반 특수문자 필터링")
    void testFormatForBooleanMode() {
        // given
        String keyword1 = "제주";
        String keyword2 = "제주 카페";
        String keyword3 = "  제주   테마파크  ";
        String keyword4 = "제주-카페"; // 연산자 포함
        String keyword5 = "+제주@테마파크!"; // 다양한 연산자/특수문자 포함
        String keyword6 = "++--@@"; // 특수문자만
        String keyword7 = "";

        // when & then
        // ngram을 쓰므로 와일드카드(*)는 붙지 않아야 함
        assertThat(placeQueryRepository.formatForBooleanMode(keyword1)).isEqualTo("+제주");
        assertThat(placeQueryRepository.formatForBooleanMode(keyword2)).isEqualTo("+제주 +카페");
        assertThat(placeQueryRepository.formatForBooleanMode(keyword3)).isEqualTo("+제주 +테마파크");
        assertThat(placeQueryRepository.formatForBooleanMode(keyword4)).isEqualTo("+제주 +카페"); // '-' 제거, 단어 분리
        assertThat(placeQueryRepository.formatForBooleanMode(keyword5)).isEqualTo("+제주 +테마파크"); // 특수문자 제거, 단어 분리
        assertThat(placeQueryRepository.formatForBooleanMode(keyword6)).isNull(); // 토큰이 없음
        assertThat(placeQueryRepository.formatForBooleanMode(keyword7)).isNull();
    }
}
