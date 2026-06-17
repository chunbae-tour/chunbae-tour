package com.chunbaetour.domain.community.free.service;

import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import com.chunbaetour.domain.community.free.storage.PostImageObjectInfo;
import com.chunbaetour.domain.community.free.storage.PostImageStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 자유게시판 이미지 S3 고아 객체 정리 스케줄러 (KAN-317).
 *
 * <p><b>왜 필요한가</b>: 자유게시판 업로드는 글 작성과 분리된 2단계라(업로드 시점엔 postId 없음), <b>업로드만 하고 글을
 * 작성하지 않으면</b>(작성 취소·이탈) 객체가 영구 고아가 된다. 글 수정/삭제 시 옛 키는 서비스가 afterCommit으로 즉시
 * 정리하지만, "미작성 업로드" 고아는 정리 주체가 없으므로 본 스케줄러가 안전망으로 회수한다(shop KAN-319 ② 패턴 재사용).
 *
 * <p><b>동작</b>: 저장소의 {@code posts/free/} prefix 객체를 나열 → 각 키가 DB(free_post_images)로 참조되는지
 * ({@code existsByImageKey}) 대조 → 미참조 객체를 삭제. prefix 한정으로 타 도메인 객체를 건드리지 않는다.
 *
 * <p><b>왜 grace가 긴가(24h)</b>: shop(1h)과 달리 자유게시판은 "사진 첨부 후 글을 한참 작성"하는 케이스가 정상이다.
 * grace가 짧으면 작성 중인 임시 업로드를 청소가 고아로 오인해 지워버린다 → 작성 완료 시 깨진 이미지. 하루 여유를 둬
 * 정상 작성 세션을 보호한다. S3 LastModified는 UTC Instant라 타임존 skew가 없다.
 *
 * <p><b>주기/락</b>: 1시간 fixedDelay + {@code @SchedulerLock}(다중 인스턴스 1회). S3 DeleteObject 멱등이라 재실행 안전.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreePostImageOrphanCleanupScheduler {

    /** 정리 대상 prefix — 자유게시판 이미지로 한정. */
    private static final String POST_IMAGE_PREFIX = "posts/free/";
    private static final long SCHEDULE_INTERVAL_MS = 3_600_000L; // 1시간
    /** grace period(시간) — 작성 중인 임시 업로드 보호를 위해 shop(1h)보다 길게(24h) 둔다. */
    private static final long GRACE_HOURS = 24L;

    private final PostImageStorage imageStorage;
    private final FreePostRepository freePostRepository;
    private final Clock clock;

    @Scheduled(fixedDelay = SCHEDULE_INTERVAL_MS)
    @SchedulerLock(name = "free_post_image_orphan_cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void cleanupOrphanObjects() {
        Instant cutoff = clock.instant().minus(Duration.ofHours(GRACE_HOURS));

        List<PostImageObjectInfo> objects;
        try {
            objects = imageStorage.listObjects(POST_IMAGE_PREFIX);
        } catch (RuntimeException e) {
            // S3 LIST 일시장애 — 이번 주기 건너뛰고 다음 주기 재시도(배치 전체 중단 방지).
            log.warn("[자유게시판 이미지 고아 정리] 객체 나열 실패, 이번 주기 건너뜀", e);
            return;
        }

        int deleted = 0;
        for (PostImageObjectInfo obj : objects) {
            // grace 이내(작성 중일 수 있는) 객체는 보류 — 다음 주기 재판정.
            if (!obj.lastModified().isBefore(cutoff)) {
                continue;
            }
            try {
                // DB가 참조하는 키면 정상 — 건드리지 않는다. 미참조 = 고아(미작성 업로드 등) → 삭제(멱등, best-effort).
                if (freePostRepository.existsByImageKey(obj.objectKey())) {
                    continue;
                }
                imageStorage.delete(obj.objectKey());
                deleted++;
                log.info("[자유게시판 이미지 고아 정리] 삭제: key={}", obj.objectKey());
            } catch (RuntimeException e) {
                // 한 건 실패로 주기 전체가 중단되지 않게 — 해당 객체만 건너뛰고 진행.
                log.warn("[자유게시판 이미지 고아 정리] 처리 실패, 건너뜀: key={}", obj.objectKey(), e);
            }
        }
        if (deleted > 0) {
            log.info("[자유게시판 이미지 고아 정리] {}건 삭제 (스캔 {}건, 기준 시각 {})", deleted, objects.size(), cutoff);
        }
    }
}
