package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.search.constant.SearchRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TypoCorrectionServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private FestivalRepository festivalRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private TypoCorrectionService typoCorrectionService;

    @BeforeEach
    void setUp() {
        // stringRedisTemplate.opsForSet() 모킹은 필요할 때만 설정 (stubbing strictness 회피)
    }

    @Test
    @DisplayName("2-gram 리스트가 정확하게 추출된다")
    void extractGrams() {
        // given
        String keyword = "경복궁";

        // when
        List<String> grams = typoCorrectionService.extractGrams(keyword);

        // then
        assertThat(grams).containsExactly("경복", "복궁");
    }

    @Test
    @DisplayName("1글자 단어는 자기 자신을 1-gram으로 추출한다")
    void extractGrams_SingleCharacter() {
        // given
        String keyword = "궁";

        // when
        List<String> grams = typoCorrectionService.extractGrams(keyword);

        // then
        assertThat(grams).containsExactly("궁");
    }

    @Test
    @DisplayName("공백이 포함된 단어는 공백 제거 후 2-gram이 추출된다")
    void extractGrams_WithSpaces() {
        // given
        String keyword = "경 복 궁";

        // when
        List<String> grams = typoCorrectionService.extractGrams(keyword);

        // then
        assertThat(grams).containsExactly("경복", "복궁");
    }

    @Test
    @DisplayName("Levenshtein 거리 계산이 정확하게 동작한다")
    void levenshtein() {
        assertThat(typoCorrectionService.levenshtein("경복궁", "경복궁")).isEqualTo(0);
        assertThat(typoCorrectionService.levenshtein("경복궁", "경북궁")).isEqualTo(1);
        assertThat(typoCorrectionService.levenshtein("경복궁", "경볻궁")).isEqualTo(1);
        assertThat(typoCorrectionService.levenshtein("경복궁", "경복구")).isEqualTo(1);
        assertThat(typoCorrectionService.levenshtein("경복궁", "경복")).isEqualTo(1); // 삭제
        assertThat(typoCorrectionService.levenshtein("경복궁", "경복궁역")).isEqualTo(1); // 삽입
        assertThat(typoCorrectionService.levenshtein("경복궁", "강북구")).isEqualTo(3);
        assertThat(typoCorrectionService.levenshtein("경복궁", "광화문")).isEqualTo(3);
    }

    @Test
    @DisplayName("오타 교정 - 가장 가까운 후보를 반환한다")
    void findClosest_ReturnsClosestCandidate() {
        // given
        String keyword = "경북궁";
        String prefix = SearchRedisKeys.TYPO_GRAM_PREFIX_PLACES;
        
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        // "경북" gram 결과
        when(setOperations.members(prefix + "경북")).thenReturn(Set.of("경북도청"));
        // "북궁" gram 결과
        when(setOperations.members(prefix + "북궁")).thenReturn(Set.of("경복궁", "창경궁")); // 창경궁 거리: 2, 경복궁 거리: 1

        // when
        Optional<String> closest = typoCorrectionService.findClosest(keyword, prefix);

        // then
        assertThat(closest).isPresent().contains("경복궁");
    }

    @Test
    @DisplayName("오타 교정 - 허용 거리(MAX_DISTANCE)를 초과하는 후보는 무시된다")
    void findClosest_IgnoresCandidatesExceedingMaxDistance() {
        // given
        String keyword = "광화수"; // "광화문"과 거리 1, "화수목"과 거리 3
        String prefix = SearchRedisKeys.TYPO_GRAM_PREFIX_PLACES;
        
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(prefix + "광화")).thenReturn(Set.of("광화문"));
        when(setOperations.members(prefix + "화수")).thenReturn(Set.of("화수목")); // 거리 3으로 MAX_DISTANCE 초과

        // when
        Optional<String> closest = typoCorrectionService.findClosest(keyword, prefix);

        // then
        assertThat(closest).isPresent().contains("광화문"); // 거리 1 후보 반환
    }

    @Test
    @DisplayName("오타 교정 - 일치하는 후보가 없으면 empty를 반환한다")
    void findClosest_ReturnsEmptyIfNoCandidates() {
        // given
        String keyword = "가나다라";
        String prefix = SearchRedisKeys.TYPO_GRAM_PREFIX_PLACES;
        
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(anyString())).thenReturn(Set.of());

        // when
        Optional<String> closest = typoCorrectionService.findClosest(keyword, prefix);

        // then
        assertThat(closest).isEmpty();
    }
}
