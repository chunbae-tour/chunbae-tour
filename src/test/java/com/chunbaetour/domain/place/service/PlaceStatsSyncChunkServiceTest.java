package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

@ExtendWith(MockitoExtension.class)
class PlaceStatsSyncChunkServiceTest {

    @InjectMocks
    private PlaceStatsSyncChunkService placeStatsSyncChunkService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

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

        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("dirty ID 파싱 실패만 poison entry로 보고 더티 set에서 제거한다")
    void syncChunk_removesOnlyInvalidDirtyId() {
        // given
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);
        given(valueOperations.multiGet(List.of("place:view:not-a-place-id"))).willReturn(List.of("10"));
        given(valueOperations.multiGet(List.of("place:like:not-a-place-id"))).willReturn(List.of("3"));

        // when
        placeStatsSyncChunkService.syncChunk(List.of("not-a-place-id"));

        // then
        verify(setOperations).remove(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, "not-a-place-id");
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("카운터 값 파싱 실패는 정상 ID를 더티 set에 남겨 다음 주기에 재처리한다")
    void syncChunk_keepsDirtyMarkerWhenCounterParsingFails() {
        // given
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(List.of("place:view:1"))).willReturn(List.of("2147483648"));
        given(valueOperations.multiGet(List.of("place:like:1"))).willReturn(List.of("3"));

        // when
        placeStatsSyncChunkService.syncChunk(List.of("1"));

        // then
        verify(stringRedisTemplate, never()).opsForSet();
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
        verify(stringRedisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("카운터 키가 모두 없으면 eviction 가능성을 고려해 더티 marker를 유지한다")
    void syncChunk_keepsDirtyMarkerWhenCountersAreMissing() {
        // given
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(List.of("place:view:1"))).willReturn(Collections.singletonList(null));
        given(valueOperations.multiGet(List.of("place:like:1"))).willReturn(Collections.singletonList(null));

        // when
        placeStatsSyncChunkService.syncChunk(List.of("1"));

        // then
        verify(stringRedisTemplate, never()).opsForSet();
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
        verify(stringRedisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("정상 동기화 후에는 읽은 Redis 값과 같은 경우에만 제거되도록 조건부 cleanup을 실행한다")
    void syncChunk_executesConditionalCleanupWithExpectedValues() {
        // given
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(List.of("place:view:1"))).willReturn(List.of("100"));
        given(valueOperations.multiGet(List.of("place:like:1"))).willReturn(Collections.singletonList(null));
        given(transactionManager.getTransaction(any(DefaultTransactionDefinition.class))).willReturn(transactionStatus);
        given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class))).willReturn(new int[] {1});

        // when
        placeStatsSyncChunkService.syncChunk(List.of("1"));

        // then
        verify(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("place:view:1", "place:like:1", PlaceRedisConstants.PLACE_DIRTY_STATS_KEY)),
                eq("100"),
                eq(""),
                eq("1")
        );
        verify(transactionManager).commit(transactionStatus);
    }
}
