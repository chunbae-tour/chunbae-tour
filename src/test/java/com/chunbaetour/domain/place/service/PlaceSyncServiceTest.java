package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.TourApiPlaceClient;
import com.chunbaetour.domain.place.client.TourApiPlaceItem;
import com.chunbaetour.domain.place.dto.response.PlaceSyncResult;
import com.chunbaetour.domain.place.service.PlaceSyncBatchService.UpsertResult;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceSyncServiceTest {

    @Mock
    private TourApiPlaceClient placeClient;
    @Mock
    private PlaceSyncBatchService batchService;
    @Mock
    private LockProvider lockProvider;
    @Mock
    private SimpleLock simpleLock;

    @InjectMocks
    private PlaceSyncService placeSyncService;

    private TourApiPlaceItem item(String contentId) {
        return new TourApiPlaceItem(contentId, "관광지" + contentId, "서울특별시", "",
                "127.0", "37.5", null, null, null, null);
    }

    @Test
    @DisplayName("락 획득 시 전 아이템을 upsert하고 결과를 집계하며, 끝에 락을 해제한다")
    void syncWithLock() {
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(simpleLock));
        given(placeClient.fetchAll()).willReturn(List.of(item("1"), item("2")));
        given(batchService.upsertItem(any()))
                .willReturn(UpsertResult.CREATED, UpsertResult.SKIPPED);

        PlaceSyncResult result = placeSyncService.syncAllPlaces();

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        verify(simpleLock).unlock();
    }

    @Test
    @DisplayName("락 미획득 시 PLACE_SYNC_IN_PROGRESS 예외를 던지고 수집을 시도하지 않는다")
    void syncWhenLocked() {
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.empty());

        assertThatThrownBy(() -> placeSyncService.syncAllPlaces())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_SYNC_IN_PROGRESS);

        verify(placeClient, never()).fetchAll();
    }

    @Test
    @DisplayName("수집 중 예외가 나도 finally에서 락을 해제한다")
    void unlockOnException() {
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(simpleLock));
        given(placeClient.fetchAll())
                .willThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR));

        assertThatThrownBy(() -> placeSyncService.syncAllPlaces())
                .isInstanceOf(BusinessException.class);

        verify(simpleLock).unlock();
    }
}
