package com.chunbaetour.domain.shop.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.shop.repository.ShopImageRepository;
import com.chunbaetour.domain.shop.storage.ShopImageObjectInfo;
import com.chunbaetour.domain.shop.storage.ShopImageStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 가게 이미지 고아 정리 스케줄러 단위 테스트 (KAN-319 ②).
 * grace period(1h) · DB 참조 여부 · prefix 한정 동작을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class ShopImageOrphanCleanupSchedulerTest {

    @Mock private ShopImageStorage imageStorage;
    @Mock private ShopImageRepository shopImageRepository;
    // 고정 시각 — now 기준 grace(1h) 경계 판정을 결정적으로.
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-17T12:00:00Z"), ZoneOffset.UTC);

    private ShopImageOrphanCleanupScheduler scheduler() {
        return new ShopImageOrphanCleanupScheduler(imageStorage, shopImageRepository, clock);
    }

    @Test
    @DisplayName("grace 초과 + DB 미참조 객체만 삭제 — 참조됨/최근 객체는 보존")
    void deletesOnlyOldUnreferencedObjects() {
        Instant now = Instant.parse("2026-06-17T12:00:00Z");
        ShopImageObjectInfo orphanOld = new ShopImageObjectInfo("shops/10/orphan.jpg", now.minusSeconds(7200)); // 2h 전, 미참조 → 삭제
        ShopImageObjectInfo referencedOld = new ShopImageObjectInfo("shops/10/keep.jpg", now.minusSeconds(7200)); // 2h 전, 참조됨 → 보존
        ShopImageObjectInfo orphanFresh = new ShopImageObjectInfo("shops/10/fresh.jpg", now.minusSeconds(600));   // 10분 전(grace 내) → 보존

        given(imageStorage.listObjects("shops/")).willReturn(List.of(orphanOld, referencedOld, orphanFresh));
        given(shopImageRepository.existsByObjectKey("shops/10/orphan.jpg")).willReturn(false);
        given(shopImageRepository.existsByObjectKey("shops/10/keep.jpg")).willReturn(true);
        // orphanFresh는 grace 내라 existsByObjectKey 조회 전에 걸러짐 → stub 불필요(strict).

        scheduler().cleanupOrphanObjects();

        verify(imageStorage).delete("shops/10/orphan.jpg");          // 고아만 삭제
        verify(imageStorage, never()).delete("shops/10/keep.jpg");   // 참조 객체 보존
        verify(imageStorage, never()).delete("shops/10/fresh.jpg");  // grace 내 보존
    }

    @Test
    @DisplayName("객체 없으면 삭제 호출 없음")
    void noObjects_noDelete() {
        given(imageStorage.listObjects("shops/")).willReturn(List.of());

        scheduler().cleanupOrphanObjects();

        // 특정 키가 아니라 어떤 키로도 삭제가 호출되지 않아야 함(키 고정 시 다른 키 삭제를 놓침)
        verify(imageStorage, never()).delete(anyString());
    }

    @Test
    @DisplayName("grace 정확 경계 — lastModified == cutoff(정확히 1h 전)는 삭제 안 함(boundary 보존)")
    void exactGraceBoundary_preserved() {
        Instant now = Instant.parse("2026-06-17T12:00:00Z");
        // 정확히 grace(1h) 전 = cutoff. !isBefore(cutoff) → true라 보존(경계는 미삭제).
        ShopImageObjectInfo atBoundary = new ShopImageObjectInfo("shops/10/edge.jpg", now.minusSeconds(3600));
        given(imageStorage.listObjects("shops/")).willReturn(List.of(atBoundary));

        scheduler().cleanupOrphanObjects();

        verify(imageStorage, never()).delete("shops/10/edge.jpg");
    }

    @Test
    @DisplayName("per-object 예외 격리 — 한 객체 처리 실패해도 다음 고아는 계속 삭제")
    void perObjectException_isolated() {
        Instant now = Instant.parse("2026-06-17T12:00:00Z");
        ShopImageObjectInfo boom = new ShopImageObjectInfo("shops/10/boom.jpg", now.minusSeconds(7200));
        ShopImageObjectInfo orphan = new ShopImageObjectInfo("shops/10/orphan.jpg", now.minusSeconds(7200));
        given(imageStorage.listObjects("shops/")).willReturn(List.of(boom, orphan));
        // 첫 객체는 existsByObjectKey에서 예외 → 건너뛰고, 둘째 고아는 정상 삭제돼야 한다
        given(shopImageRepository.existsByObjectKey("shops/10/boom.jpg")).willThrow(new RuntimeException("DB 일시장애"));
        given(shopImageRepository.existsByObjectKey("shops/10/orphan.jpg")).willReturn(false);

        scheduler().cleanupOrphanObjects();

        verify(imageStorage, never()).delete("shops/10/boom.jpg"); // 실패 건은 미삭제
        verify(imageStorage).delete("shops/10/orphan.jpg");        // 다음 고아는 계속 처리
    }

    @Test
    @DisplayName("listObjects 실패 — 이번 주기 건너뜀(예외 전파 없음)")
    void listFails_skipsCycle() {
        given(imageStorage.listObjects("shops/")).willThrow(new RuntimeException("S3 LIST 장애"));

        scheduler().cleanupOrphanObjects(); // throw 없이 조용히 종료

        // 특정 키가 아니라 어떤 키로도 삭제가 호출되지 않아야 함(키 고정 시 다른 키 삭제를 놓침)
        verify(imageStorage, never()).delete(anyString());
    }
}
