package com.chunbaetour.domain.admin.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영 액션 기록 서비스 (KAN-179, Admin Epic KAN-177 S01).
 *
 * <p>두 가지 핵심 패턴
 * <ol>
 *   <li><b>afterCommit + REQUIRES_NEW</b> — 호출자(본 요청) 트랜잭션이 commit된 직후에만 별도 트랜잭션으로 save.
 *       <ul>
 *         <li>본 요청이 rollback 되면 afterCommit이 호출되지 않으므로 로그가 발행되지 않는다 → "액션이 실제로
 *             일어나지 않은 경우 로그도 미발행" 보장.</li>
 *         <li>commit 직후 REQUIRES_NEW로 별도 트랜잭션을 새로 열어 save → 로그 save 트랜잭션이 본 요청 트랜잭션과
 *             독립. 로그 save 실패가 본 요청 트랜잭션에 영향 X.</li>
 *         <li>KAN-143 회원 탈퇴 afterCommit + KAN-105 보안 감사 흡수 패턴 일관.</li>
 *       </ul>
 *   </li>
 *   <li><b>로그 실패 흡수</b> — save 시 어떤 예외가 발생해도 try-catch로 잡고 {@code log.error}로 기록만.
 *       호출자 응답에 영향 X. 로그 실패가 본 요청 결과를 깨면 운영 액션 자체가 실패하는 함정을 차단.</li>
 * </ol>
 *
 * <p>트랜잭션 컨텍스트 부재 시 — {@link TransactionSynchronizationManager#isSynchronizationActive()}가 false면
 * 즉시 save 실행. 테스트/스케줄러 등 트랜잭션 밖 호출 케이스 대비.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminActionLogService {

    private final AdminActionLogRepository repository;

    /**
     * REQUIRES_NEW 전용 TransactionTemplate ({@link AdminAuditConfig}에서 정의).
     *
     * <p>본 요청 트랜잭션과 독립된 새 트랜잭션을 열어 save한다. 본 요청이 rollback돼도 commit된 로그는 보존,
     * 로그 save 실패가 본 요청 트랜잭션을 깨지도 않도록 격리.
     */
    private final @Qualifier(AdminAuditConfig.REQUIRES_NEW_TEMPLATE_BEAN) TransactionTemplate requiresNewTemplate;

    /**
     * 운영 액션 1건을 기록한다.
     *
     * <p>분기
     * <ul>
     *   <li><b>쓰기 트랜잭션 안</b>(active && !readOnly) → afterCommit 콜백 등록. commit 직후 별도
     *       REQUIRES_NEW 트랜잭션에서 save.</li>
     *   <li><b>트랜잭션 밖</b>(!active) → 즉시 save (테스트/스케줄러 등).</li>
     *   <li><b>readOnly 트랜잭션 안</b>(active && readOnly) → <b>미발행</b>. 쓰기 액션이 아니므로 audit을
     *       남기지 않는다. {@code @LogAdminAction}이 readOnly 메서드에 잘못 부착된 경우의 안전 가드.</li>
     * </ul>
     *
     * <p>{@code context}가 null이면 즉시 return — 이후 흡수 catch가 {@code context.adminUserId()}를 재참조해
     * 2차 NPE를 던지는 것을 차단한다.
     */
    public void record(AdminActionContext context) {
        if (context == null) {
            log.warn("AdminActionLog skipped — null context");
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    saveInNewTx(context);
                }
            });
        } else if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            saveInNewTx(context);
        }
        // readOnly 트랜잭션이면 미발행 (write 액션이 아님)
    }

    /**
     * 별도 트랜잭션 안에서 save. 어떤 예외도 흡수 — 본 요청 응답에 영향이 가지 않도록.
     *
     * <p>로그 흡수 정책의 trade-off: 운영 액션 자체는 성공한 채로 audit 누락이 발생할 수 있다. 그러나
     * "audit 누락"보다 "운영 액션이 사용자에게 실패로 보임"이 더 큰 사고이므로 흡수가 표준 — 실패 사실은
     * {@code log.error}로 운영 보안 감사 로거에 남아 운영자가 후속 확인 가능.
     */
    private void saveInNewTx(AdminActionContext context) {
        try {
            requiresNewTemplate.executeWithoutResult(status -> repository.save(AdminActionLog.from(context)));
        } catch (Exception e) {
            log.error(
                    "AdminActionLog save failed (absorbed) — adminUserId={}, action={}, target={}:{}",
                    context.adminUserId(),
                    context.actionType(),
                    context.targetType(),
                    context.targetId(),
                    e);
        }
    }
}
