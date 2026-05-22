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
        Optional<List<String>> result = suggestCacheRepository.get(prefix);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Redis 쓰기 중 예외가 발생해도 로직이 중단되지 않는다 (예외 격리)")
    void set_DoesNotThrowException_WhenRedisThrowsException() {
        // given
        String prefix = "경복";
        List<String> suggestions = List.of("경복궁");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("Redis Down"))
                .when(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));

        // when & then (예외가 던져지지 않아야 함)
        org.assertj.core.api.Assertions.assertThatCode(() -> suggestCacheRepository.set(prefix, suggestions))
                .doesNotThrowAnyException();
    }
}
