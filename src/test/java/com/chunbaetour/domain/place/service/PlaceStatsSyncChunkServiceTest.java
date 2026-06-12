package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PlaceStatsSyncChunkServiceTest {

    @InjectMocks
    private PlaceStatsSyncChunkService placeStatsSyncChunkService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("Redis multiGet이 null이면 청크 실패로 드러나도록 예외를 던진다")
    void syncChunk_throwsWhenRedisMultiGetReturnsNull() {
        // given
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(List.of("place:view:1"))).willReturn(null);
        given(valueOperations.multiGet(List.of("place:like:1"))).willReturn(List.of("10"));

        // when & then
        assertThatThrownBy(() -> placeStatsSyncChunkService.syncChunk(List.of("1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis multiGet returned null");

        verify(jdbcTemplate, never()).batchUpdate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.BatchPreparedStatementSetter.class)
        );
    }
}
