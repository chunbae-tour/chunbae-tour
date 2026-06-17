package com.chunbaetour.domain.community.free.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import com.chunbaetour.domain.community.free.storage.PostImageObjectInfo;
import com.chunbaetour.domain.community.free.storage.PostImageStorage;
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
 * 자유게시판 이미지 고아 정리 스케줄러 단위 테스트 (KAN-317).
 * grace period(24h) · DB 참조 여부 · 예외 격리 동작 고정.
 */
@ExtendWith(MockitoExtension.class)
class FreePostImageOrphanCleanupSchedulerTest {

    @Mock private PostImageStorage imageStorage;
    @Mock private FreePostRepository freePostRepository;
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-17T12:00:00Z"), ZoneOffset.UTC);

    private FreePostImageOrphanCleanupScheduler scheduler() {
        return new FreePostImageOrphanCleanupScheduler(imageStorage, freePostRepository, clock);
    }

    @Test
    @DisplayName("grace(24h) 초과 + DB 미참조만 삭제 — 참조됨/최근 객체는 보존")
    void deletesOnlyOldUnreferenced() {
        Instant now = Instant.parse("2026-06-17T12:00:00Z");
        PostImageObjectInfo orphanOld = new PostImageObjectInfo("posts/free/7/orphan.jpg", now.minusSeconds(90_000));   // ~25h, 미참조 → 삭제
        PostImageObjectInfo referencedOld = new PostImageObjectInfo("posts/free/7/keep.jpg", now.minusSeconds(90_000)); // ~25h, 참조됨 → 보존
        PostImageObjectInfo fresh = new PostImageObjectInfo("posts/free/7/fresh.jpg", now.minusSeconds(3_600));         // 1h(grace 내) → 보존

        given(imageStorage.listObjects("posts/free/")).willReturn(List.of(orphanOld, referencedOld, fresh));
        given(freePostRepository.existsByImageKey("posts/free/7/orphan.jpg")).willReturn(false);
        given(freePostRepository.existsByImageKey("posts/free/7/keep.jpg")).willReturn(true);

        scheduler().cleanupOrphanObjects();

        verify(imageStorage).delete("posts/free/7/orphan.jpg");
        verify(imageStorage, never()).delete("posts/free/7/keep.jpg");
        verify(imageStorage, never()).delete("posts/free/7/fresh.jpg");
    }

    @Test
    @DisplayName("grace 정확 경계 — lastModified == cutoff(정확히 24h 전)는 보존")
    void exactBoundaryPreserved() {
        Instant now = Instant.parse("2026-06-17T12:00:00Z");
        PostImageObjectInfo edge = new PostImageObjectInfo("posts/free/7/edge.jpg", now.minusSeconds(86_400)); // 정확히 24h
        given(imageStorage.listObjects("posts/free/")).willReturn(List.of(edge));

        scheduler().cleanupOrphanObjects();

        verify(imageStorage, never()).delete("posts/free/7/edge.jpg");
    }

    @Test
    @DisplayName("per-object 예외 격리 — 한 건 실패해도 다음 고아는 계속 삭제")
    void perObjectExceptionIsolated() {
        Instant now = Instant.parse("2026-06-17T12:00:00Z");
        PostImageObjectInfo boom = new PostImageObjectInfo("posts/free/7/boom.jpg", now.minusSeconds(90_000));
        PostImageObjectInfo orphan = new PostImageObjectInfo("posts/free/7/orphan.jpg", now.minusSeconds(90_000));
        given(imageStorage.listObjects("posts/free/")).willReturn(List.of(boom, orphan));
        given(freePostRepository.existsByImageKey("posts/free/7/boom.jpg")).willThrow(new RuntimeException("DB 장애"));
        given(freePostRepository.existsByImageKey("posts/free/7/orphan.jpg")).willReturn(false);

        scheduler().cleanupOrphanObjects();

        verify(imageStorage, never()).delete("posts/free/7/boom.jpg");
        verify(imageStorage).delete("posts/free/7/orphan.jpg");
    }

    @Test
    @DisplayName("listObjects 실패 — 이번 주기 건너뜀(예외 전파 없음)")
    void listFailsSkips() {
        given(imageStorage.listObjects("posts/free/")).willThrow(new RuntimeException("S3 장애"));

        scheduler().cleanupOrphanObjects();

        verify(imageStorage, never()).delete(eq("posts/free/7/x.jpg"));
    }
}
