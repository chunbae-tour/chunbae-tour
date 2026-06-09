package com.chunbaetour.domain.search.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;
import com.chunbaetour.domain.search.dto.response.SuggestResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestCacheRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SuggestCacheRepository suggestCacheRepository;

    @Test
    @DisplayName("Redis 읽기 중 예외가 발생하면 예외를 무시하고 Optional.empty()를 반환한다 (DB fallback 유도)")
    void get_ReturnsEmptyOptional_WhenRedisThrowsException() {
        // given
        String prefix = "경복";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis Down"));

        // when
        Optional<List<SuggestResponse>> result = suggestCacheRepository.get(prefix);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Redis 쓰기 중 예외가 발생해도 로직이 중단되지 않는다 (예외 격리)")
    void set_DoesNotThrowException_WhenRedisThrowsException() {
        // given
        String prefix = "경복";
        List<SuggestResponse> suggestions = List.of(new SuggestResponse("경복궁", SuggestResponse.SuggestSource.DB));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("Redis Down"))
                .when(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));

        // when & then (예외가 던져지지 않아야 함)
        org.assertj.core.api.Assertions.assertThatCode(() -> suggestCacheRepository.set(prefix, suggestions))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("캐싱된 문자열이 빈 배열 JSON 일 때 Optional.of(emptyList())를 반환한다")
    void get_ReturnsEmptyList_WhenCachedIsEmptyJsonArray() throws Exception {
        // given
        String prefix = "없는검색어";
        String cachedJson = "[]";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(cachedJson);
        
        when(objectMapper.readValue(org.mockito.ArgumentMatchers.eq(cachedJson), org.mockito.ArgumentMatchers.<tools.jackson.core.type.TypeReference<List<SuggestResponse>>>any()))
                .thenReturn(List.of());

        // when
        Optional<List<SuggestResponse>> result = suggestCacheRepository.get(prefix);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    @DisplayName("캐싱된 문자열이 정상 JSON 일 때 역직렬화하여 반환한다")
    void get_ReturnsList_WhenCachedIsNormalJsonArray() throws Exception {
        // given
        String prefix = "경복";
        String cachedJson = "[{\"keyword\":\"경복궁\",\"source\":\"DB\"}]";
        List<SuggestResponse> expectedResponses = List.of(new SuggestResponse("경복궁", SuggestResponse.SuggestSource.DB));
        
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(cachedJson);
        
        when(objectMapper.readValue(org.mockito.ArgumentMatchers.eq(cachedJson), org.mockito.ArgumentMatchers.<tools.jackson.core.type.TypeReference<List<SuggestResponse>>>any()))
                .thenReturn(expectedResponses);

        // when
        Optional<List<SuggestResponse>> result = suggestCacheRepository.get(prefix);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).keyword()).isEqualTo("경복궁");
    }

    @Test
    @DisplayName("빈 리스트 캐시 저장 시 JSON 직렬화 결과를 TTL_EMPTY와 함께 저장한다")
    void set_SavesEmptyListWithEmptyTtl() throws Exception {
        // given
        String prefix = "없는검색어";
        List<SuggestResponse> emptyList = List.of();
        String json = "[]";
        
        when(objectMapper.writeValueAsString(emptyList)).thenReturn(json);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // when
        suggestCacheRepository.set(prefix, emptyList);

        // then
        verify(valueOperations).set(org.mockito.ArgumentMatchers.contains("search:suggest:"), org.mockito.ArgumentMatchers.eq(json), org.mockito.ArgumentMatchers.eq(java.time.Duration.ofSeconds(10)));
    }
}
