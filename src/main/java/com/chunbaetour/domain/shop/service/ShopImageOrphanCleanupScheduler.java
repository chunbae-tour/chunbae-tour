package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.shop.repository.ShopImageRepository;
import com.chunbaetour.domain.shop.storage.ShopImageObjectInfo;
import com.chunbaetour.domain.shop.storage.ShopImageStorage;
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
 * 가게 사진 S3 고아 객체 정리 스케줄러 (KAN-319 ②, B24).
 *
 * <p><b>왜 필요한가</b>: 업로드 1단계화(KAN-315) 이후에도 "S3 객체는 있는데 DB 행이 없는" 고아가 예외 경로에서 남는다.
 * 업로드 롤백은 보상 훅({@link ShopImageService} {@code deleteObjectAfterRollback}, KAN-319 ①)이 즉시 회수하지만,
 * <b>커밋 직전 JVM 크래시·삭제 afterCommit 실패</b> 같은 잔여는 훅으로 못 잡는다. 본 스케줄러가 안전망으로 쓸어담는다.
 *
 * <p><b>동작</b>: 저장소의 {@code shops/} prefix 객체를 나열 → 각 키가 DB 행으로 참조되는지({@code existsByObjectKey})
 * 대조 → 미참조 객체를 삭제한다. 정리 대상은 {@code shops/} prefix로 한정해 타 도메인 객체를 건드리지 않는다.
 *
 * <p><b>왜 grace period가 필수인가</b>: 방금 업로드돼 아직 DB 커밋 전(또는 ① 보상 직전)인 객체를 청소가 고아로
 * 오인해 지우면, 정상 업로드가 깨진다. 객체 수정 시각이 {@code GRACE}({@value GRACE_HOURS}h)보다 오래된 것만 삭제 대상으로
 * 삼아 이 race를 차단한다. S3 LastModified는 UTC Instant라 {@code createdAt}(KST wall-clock) 같은 타임존 skew가 없다.
 *
 * <p><b>주기/락</b>: 1시간 fixedDelay. 다중 ECS 인스턴스는 {@code @SchedulerLock}으로 한 인스턴스만 실행한다
 * (KAN-298 {@code PendingOrderReconcileScheduler} 패턴). 객체 삭제는 멱등(S3 DeleteObject)이라 재실행도 안전하다.
 *
 * <p><b>규모 메모</b>: 매 실행 prefix 전체 LIST라 객체가 급증하면 비용↑ → 후속으로 "고아 후보 테이블"(업로드/삭제 실패
 * 시 key 기록)만 처리하는 방식으로 최적화 여지. MVP는 prefix scan으로 충분(B24).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopImageOrphanCleanupScheduler {

    /** 정리 대상 prefix — 가게 이미지로 한정(타 도메인 객체 미접촉). */
    private static final String SHOP_IMAGE_PREFIX = "shops/";
    private static final long SCHEDULE_INTERVAL_MS = 3_600_000L; // 1시간
    /** grace period(시간) — 이보다 최근에 수정된 객체는 커밋 전/보상 전일 수 있어 삭제하지 않는다. */
    private static final long GRACE_HOURS = 1L;

    private final ShopImageStorage imageStorage;
    private final ShopImageRepository shopImageRepository;
    private final Clock clock;

    @Scheduled(fixedDelay = SCHEDULE_INTERVAL_MS)
    @SchedulerLock(name = "shop_image_orphan_cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void cleanupOrphanObjects() {
        Instant cutoff = clock.instant().minus(Duration.ofHours(GRACE_HOURS));

        List<ShopImageObjectInfo> objects = imageStorage.listObjects(SHOP_IMAGE_PREFIX);
        int deleted = 0;
        for (ShopImageObjectInfo obj : objects) {
            // grace 이내(갓 올라온) 객체는 커밋 전/롤백보상 전일 수 있어 보류 — 다음 주기에 재판정.
            if (!obj.lastModified().isBefore(cutoff)) {
                continue;
            }
            // DB 행이 참조하는 키면 정상 객체 — 건드리지 않는다. 미참조 = 고아 → 삭제(멱등, best-effort).
            if (shopImageRepository.existsByObjectKey(obj.objectKey())) {
                continue;
            }
            imageStorage.delete(obj.objectKey());
            deleted++;
            log.info("[가게 이미지 고아 정리] 삭제: key={}", obj.objectKey());
        }
        if (deleted > 0) {
            log.info("[가게 이미지 고아 정리] {}건 삭제 (스캔 {}건, 기준 시각 {})", deleted, objects.size(), cutoff);
        }
    }
}
