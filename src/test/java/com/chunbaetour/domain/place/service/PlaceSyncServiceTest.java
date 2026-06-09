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
import com.chunbaetour.domain.place.entity.PlaceSyncState;
import com.chunbaetour.domain.place.repository.PlaceSyncStateRepository;
import com.chunbaetour.domain.place.service.PlaceSyncBatchService.UpsertResult;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.dao.DataIntegrityViolationException;
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
    private PlaceSyncStateRepository syncStateRepository;
    @Mock
    private LockProvider lockProvider;
    @Mock
    private SimpleLock simpleLock;

    @InjectMocks
    private PlaceSyncService placeSyncService;

    private TourApiPlaceItem item(String contentId, String modifiedTime) {
        return new TourApiPlaceItem(contentId, "관광지" + contentId, "서울특별시", "",
                "127.0", "37.5", null, null, null, modifiedTime, "1", "1");
    }

    private TourApiPlaceItem deletedItem(String contentId, String modifiedTime) {
        return new TourApiPlaceItem(contentId, "삭제됨", "서울특별시", "",
                "127.0", "37.5", null, null, null, modifiedTime, "0", "1"); // showflag != "1"
    }

    @Test
    @DisplayName("락 획득 시 변경분을 upsert·삭제 반영하고, 최신 modifiedtime을 저장하며, 끝에 락을 해제한다")
    void syncWithLock() {
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(simpleLock));
        given(syncStateRepository.findById(PlaceSyncState.SINGLETON_ID)).willReturn(Optional.empty());
        given(placeClient.fetchModifiedSince(any())).willReturn(List.of(
                item("1", "20260101000001"),
                deletedItem("2", "20260101000003"),
                item("3", "20260101000002")));
        given(batchService.upsertItem(any())).willReturn(UpsertResult.CREATED, UpsertResult.UPDATED);
        given(batchService.markDeleted("2")).willReturn(UpsertResult.DELETED);

        PlaceSyncResult result = placeSyncService.syncAllPlaces();

        assertThat(result.fetched()).isEqualTo(3);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(1);
        // 가장 최신 modifiedtime 저장
        verify(batchService).saveLastModifiedTime("20260101000003");
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

        verify(placeClient, never()).fetchModifiedSince(any());
    }

    @Test
    @DisplayName("처리 실패한 item의 modifiedtime이 최신보다 과거면, 경계를 그 실패 item 이전으로 캡해 다음 run 재수집을 보장한다")
    void boundaryCappedByOlderFailedItem() {
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(simpleLock));
        given(syncStateRepository.findById(PlaceSyncState.SINGLETON_ID)).willReturn(Optional.empty());
        // 목록 순서: 성공(최신 0003) → 실패(과거 0001)
        given(placeClient.fetchModifiedSince(any())).willReturn(List.of(
                item("1", "20260101000003"),
                item("2", "20260101000001")));
        // 첫 item은 성공, 두 번째 item은 처리 중 예외
        given(batchService.upsertItem(any()))
                .willReturn(UpsertResult.CREATED)
                .willThrow(new RuntimeException("boom"));

        PlaceSyncResult result = placeSyncService.syncAllPlaces();

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        // 성공 최신(0003)이 아니라 실패 item(0001)으로 경계가 캡되어야 함 → 다음 run에서 실패 item 재수집
        verify(batchService).saveLastModifiedTime("20260101000001");
    }

    @Test
    @DisplayName("처리 실패한 item이 최신이어도, 경계는 성공한 item까지만 전진해 실패 item 재수집을 보장한다")
    void boundaryDoesNotAdvancePastNewerFailedItem() {
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(simpleLock));
        given(syncStateRepository.findById(PlaceSyncState.SINGLETON_ID)).willReturn(Optional.empty());
        // 목록 순서: 성공(과거 0001) → 실패(최신 0003)
        given(placeClient.fetchModifiedSince(any())).willReturn(List.of(
                item("1", "20260101000001"),
                item("2", "20260101000003")));
        given(batchService.upsertItem(any()))
                .willReturn(UpsertResult.CREATED)
                .willThrow(new RuntimeException("boom"));

        PlaceSyncResult result = placeSyncService.syncAllPlaces();

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        // 경계는 성공한 item(0001)까지만 — 실패 item(0003)은 다음 run에 재수집됨
        verify(batchService).saveLastModifiedTime("20260101000001");
    }

    @Test
    @DisplayName("modifiedtime이 공백/빈 문자열인 item만 있으면 경계로 빈 값을 저장하지 않는다")
    void blankModifiedTimeNotSavedAsBoundary() {
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(simpleLock));
        given(syncStateRepository.findById(PlaceSyncState.SINGLETON_ID)).willReturn(Optional.empty());
        given(placeClient.fetchModifiedSince(any())).willReturn(List.of(
                item("1", "   "),
                item("2", "")));
        given(batchService.upsertItem(any())).willReturn(UpsertResult.CREATED, UpsertResult.CREATED);

        placeSyncService.syncAllPlaces();

        // 공백은 null로 정규화 → 경계 전진 없음 → saveLastModifiedTime 미호출(빈 값 저장 방지)
        verify(batchService, never()).saveLastModifiedTime(any());
    }

    @Test
    @DisplayName("modifiedtime이 14자리 형식이 아니면(비공백 이상값) 경계로 저장하지 않는다")
    void malformedModifiedTimeNotSavedAsBoundary() {
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(simpleLock));
        given(syncStateRepository.findById(PlaceSyncState.SINGLETON_ID)).willReturn(Optional.empty());
        given(placeClient.fetchModifiedSince(any())).willReturn(List.of(
                item("1", "2026"),       // 14자리 아님
                item("2", "abcd12345678")));  // 숫자 아님
        given(batchService.upsertItem(any())).willReturn(UpsertResult.CREATED, UpsertResult.CREATED);

        placeSyncService.syncAllPlaces();

        // 이상 포맷은 null로 정규화 → 경계 전진 없음 → 빈/깨진 값이 경계로 저장되지 않음
        verify(batchService, never()).saveLastModifiedTime(any());
    }

    @Test
    @DisplayName("retryable 실패와 영구 실패가 섞이면 경계는 retryable 실패 기준으로만 캡된다")
    void mixedFailuresCapByRetryableOnly() {
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(simpleLock));
        given(syncStateRepository.findById(PlaceSyncState.SINGLETON_ID)).willReturn(Optional.empty());
        // 성공(0005) → 영구실패(0001, 가장 과거) → retryable 실패(0003)
        given(placeClient.fetchModifiedSince(any())).willReturn(List.of(
                item("1", "20260101000005"),
                item("2", "20260101000001"),
                item("3", "20260101000003")));
        given(batchService.upsertItem(any()))
                .willReturn(UpsertResult.CREATED)
                .willThrow(new DataIntegrityViolationException("constraint"))
                .willThrow(new RuntimeException("transient"));

        placeSyncService.syncAllPlaces();

        // 영구실패(0001)는 경계를 끌어내리지 않고, retryable(0003)만 캡 → boundary = min(0005, 0003) = 0003
        verify(batchService).saveLastModifiedTime("20260101000003");
    }

    @Test
    @DisplayName("제약 위반(DataIntegrityViolationException)은 영구 실패로 보고 경계를 막지 않는다(다음 run 대량 재수집 방지)")
    void permanentFailureDoesNotCapBoundary() {
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(simpleLock));
        given(syncStateRepository.findById(PlaceSyncState.SINGLETON_ID)).willReturn(Optional.empty());
        // 성공(최신 0003) → 제약 위반 실패(과거 0001)
        given(placeClient.fetchModifiedSince(any())).willReturn(List.of(
                item("1", "20260101000003"),
                item("2", "20260101000001")));
        given(batchService.upsertItem(any()))
                .willReturn(UpsertResult.CREATED)
                .willThrow(new DataIntegrityViolationException("constraint"));

        placeSyncService.syncAllPlaces();

        // 영구 실패는 경계 캡 대상이 아니므로 성공 최신(0003)까지 경계가 전진해야 함
        verify(batchService).saveLastModifiedTime("20260101000003");
    }

    @Test
    @DisplayName("전부 제약 위반(영구 실패)인 run에서도 경계가 최신 실패 item까지 전진해 다음 run 재수집을 막는다")
    void allPermanentFailuresStillAdvanceBoundary() {
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(simpleLock));
        given(syncStateRepository.findById(PlaceSyncState.SINGLETON_ID)).willReturn(Optional.empty());
        // 성공 건 0 — 전부 DataIntegrityViolationException(영구 실패). 경계가 since에 묶이면 매 run 동일 poison 재수집.
        given(placeClient.fetchModifiedSince(any())).willReturn(List.of(
                item("1", "20260101000001"),
                item("2", "20260101000003")));
        given(batchService.upsertItem(any()))
                .willThrow(new DataIntegrityViolationException("constraint"));

        placeSyncService.syncAllPlaces();

        // 영구 실패는 경계 캡 대상이 아니므로 최신 실패 item(0003)까지 경계가 전진해야 함 (poison 영구 점유 방지)
        verify(batchService).saveLastModifiedTime("20260101000003");
    }

    @Test
    @DisplayName("수집 중 예외가 나도 finally에서 락을 해제한다")
    void unlockOnException() {
        given(lockProvider.lock(any(LockConfiguration.class))).willReturn(Optional.of(simpleLock));
        given(syncStateRepository.findById(PlaceSyncState.SINGLETON_ID)).willReturn(Optional.empty());
        given(placeClient.fetchModifiedSince(any()))
                .willThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR));

        assertThatThrownBy(() -> placeSyncService.syncAllPlaces())
                .isInstanceOf(BusinessException.class);

        verify(simpleLock).unlock();
    }
}
