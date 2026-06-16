package com.chunbaetour.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 상인 홈 대시보드 신규 쿼리(KAN-283) 실DB 검증.
 * mock 단위 테스트가 못 잡는 ① 인터페이스 프로젝션 alias↔getter 매핑 ② 정렬(completedAt DESC, id DESC)
 * ③ 경계(startAt 포함, endAt 제외) ④ 상태 필터(REJECTED+EXPIRED만, CANCELLED 제외)를 Testcontainers MySQL로 검증한다.
 */
@SpringBootTest
class QrPayRequestRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private QrPayRequestRepository repository;

    private static final Long SHOP_ID = 777L;
    // 검증 윈도우 [START, END)
    private static final LocalDateTime START = LocalDateTime.of(2026, 5, 24, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 5, 25, 0, 0);

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("findCompletedByShopsBetween — 프로젝션 4필드 매핑 + completedAt DESC 정렬 + 경계(start 포함/end 제외)")
    void findCompleted_projection_ordering_boundary() {
        persistCompleted("c-mid", 5_000L, LocalDateTime.of(2026, 5, 24, 12, 0));
        persistCompleted("c-early", 3_000L, LocalDateTime.of(2026, 5, 24, 10, 0));
        persistCompleted("c-start", 1_000L, START);                       // == START → 포함
        persistCompleted("c-end", 9_999L, END);                           // == END → 제외(strict <)
        persistCompleted("c-before", 9_999L, START.minusSeconds(1));      // 윈도우 이전 → 제외

        List<CompletedQrPayView> result = repository.findCompletedByShopsBetween(
                List.of(SHOP_ID), QrPayStatus.COMPLETED, START, END);

        // 경계 + 정렬(완료 시각 내림차순)
        assertThat(result).extracting(CompletedQrPayView::getPayRequestId)
                .containsExactly("c-mid", "c-early", "c-start");

        // 프로젝션 4필드가 alias↔getter로 정상 매핑(non-null)
        CompletedQrPayView first = result.get(0);
        assertThat(first.getPayRequestId()).isEqualTo("c-mid");
        assertThat(first.getShopId()).isEqualTo(SHOP_ID);
        assertThat(first.getAmount()).isEqualTo(5_000L);
        assertThat(first.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 5, 24, 12, 0));
    }

    @Test
    @DisplayName("findCompletedByShopsBetween — 동일 completedAt이면 id DESC로 정렬한다")
    void findCompleted_sameInstant_ordersByIdDesc() {
        LocalDateTime sameInstant = LocalDateTime.of(2026, 5, 24, 11, 0);
        persistCompleted("c-a", 1_000L, sameInstant);  // 먼저 저장 → 작은 id
        persistCompleted("c-b", 2_000L, sameInstant);  // 나중 저장 → 큰 id

        List<CompletedQrPayView> result = repository.findCompletedByShopsBetween(
                List.of(SHOP_ID), QrPayStatus.COMPLETED, START, END);

        assertThat(result).extracting(CompletedQrPayView::getPayRequestId)
                .containsExactly("c-b", "c-a");
    }

    @Test
    @DisplayName("countByShopsAndStatusesBetween — REJECTED+EXPIRED만 세고 CANCELLED·COMPLETED 제외, expiredAt 경계 적용")
    void count_rejectedExpiredOnly_excludesCancelled() {
        persistRejected("r-in", LocalDateTime.of(2026, 5, 24, 9, 0));     // 포함
        persistExpired("e-in", LocalDateTime.of(2026, 5, 24, 20, 0));     // 포함
        persistCancelled("x-in", LocalDateTime.of(2026, 5, 24, 15, 0));   // 상태 제외
        persistRejected("r-end", END);                                    // expiredAt == END → 경계 제외
        persistCompleted("c-in", 1_000L, LocalDateTime.of(2026, 5, 24, 13, 0)); // 상태 제외

        long count = repository.countByShopsAndStatusesBetween(
                List.of(SHOP_ID), List.of(QrPayStatus.REJECTED, QrPayStatus.EXPIRED), START, END);

        assertThat(count).isEqualTo(2L);
    }

    // ── 영속 헬퍼: 도메인 메서드로 상태 전이 후 저장 (전이 시 pendingKey null → unique 충돌 없음) ──

    private void persistCompleted(String payRequestId, long amount, LocalDateTime completedAt) {
        QrPayRequest r = QrPayRequest.create(payRequestId, 1L, SHOP_ID, amount, "[]", completedAt.plusMinutes(5));
        r.complete(completedAt);
        repository.saveAndFlush(r);
    }

    private void persistRejected(String payRequestId, LocalDateTime expiredAt) {
        QrPayRequest r = QrPayRequest.create(payRequestId, 1L, SHOP_ID, 1_000L, "[]", expiredAt);
        r.reject("test");
        repository.saveAndFlush(r);
    }

    private void persistExpired(String payRequestId, LocalDateTime expiredAt) {
        QrPayRequest r = QrPayRequest.create(payRequestId, 1L, SHOP_ID, 1_000L, "[]", expiredAt);
        r.expire();
        repository.saveAndFlush(r);
    }

    private void persistCancelled(String payRequestId, LocalDateTime expiredAt) {
        QrPayRequest r = QrPayRequest.create(payRequestId, 1L, SHOP_ID, 1_000L, "[]", expiredAt);
        r.cancel();
        repository.saveAndFlush(r);
    }
}
