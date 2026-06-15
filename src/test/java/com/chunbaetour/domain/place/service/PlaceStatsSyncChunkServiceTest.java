package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
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
    @DisplayName("dirty ID parsing failure removes only the poison entry")
    void syncChunk_removesOnlyInvalidDirtyId() {
        // given
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);

        // when
        placeStatsSyncChunkService.syncChunk(List.of("not-a-place-id"));

        // then
        verify(setOperations).remove(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, "not-a-place-id");
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("counter parsing failure keeps the dirty marker for retry")
    void syncChunk_keepsDirtyMarkerWhenCounterParsingFails() {
        // given
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("place:view:1")).willReturn("2147483648");
        given(valueOperations.get("place:like:1")).willReturn("3");

        // when
        placeStatsSyncChunkService.syncChunk(List.of("1"));

        // then
        verify(stringRedisTemplate, never()).opsForSet();
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("missing counters keep the dirty marker for retry")
    void syncChunk_keepsDirtyMarkerWhenCountersAreMissing() {
        // given
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("place:view:1")).willReturn(null);
        given(valueOperations.get("place:like:1")).willReturn(null);

        // when
        placeStatsSyncChunkService.syncChunk(List.of("1"));

        // then
        verify(stringRedisTemplate, never()).opsForSet();
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("synced dirty marker is removed when counters are unchanged")
    void syncChunk_removesDirtyMarkerWhenCountersAreUnchanged() {
        // given
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);
        given(valueOperations.get("place:view:1")).willReturn("100");
        given(valueOperations.get("place:like:1")).willReturn(null);
        given(transactionManager.getTransaction(any(DefaultTransactionDefinition.class))).willReturn(transactionStatus);
        given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class))).willReturn(new int[] {1});

        // when
        placeStatsSyncChunkService.syncChunk(List.of("1"));

        // then
        verify(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
        verify(setOperations).remove(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, "1");
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    @DisplayName("dirty marker is restored when counters change during cleanup")
    void syncChunk_restoresDirtyMarkerWhenCountersChangeDuringCleanup() {
        // given
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);
        given(valueOperations.get("place:view:1")).willReturn("100", "100", "101");
        given(valueOperations.get("place:like:1")).willReturn("10");
        given(transactionManager.getTransaction(any(DefaultTransactionDefinition.class))).willReturn(transactionStatus);
        given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class))).willReturn(new int[] {1});

        // when
        placeStatsSyncChunkService.syncChunk(List.of("1"));

        // then
        verify(setOperations).remove(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, "1");
        verify(setOperations).add(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, "1");
    }

    @Test
    @DisplayName("batchUpdate failure rolls back transaction and keeps dirty marker")
    void syncChunk_rollsBackTransactionWhenBatchUpdateFails() {
        // given
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("place:view:1")).willReturn("100");
        given(valueOperations.get("place:like:1")).willReturn("10");
        given(transactionManager.getTransaction(any(DefaultTransactionDefinition.class))).willReturn(transactionStatus);
        given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .willThrow(new DataAccessException("DB connection failed") {});

        // when & then
        assertThatThrownBy(() -> placeStatsSyncChunkService.syncChunk(List.of("1")))
                .isInstanceOf(DataAccessException.class);

        verify(transactionManager).rollback(transactionStatus);
        verify(stringRedisTemplate, never()).opsForSet();
    }
}