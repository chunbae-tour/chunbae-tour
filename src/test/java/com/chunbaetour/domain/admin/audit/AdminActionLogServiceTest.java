package com.chunbaetour.domain.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link AdminActionLogService} 단위 테스트 (KAN-179).
 *
 * <p>핵심 검증
 * <ul>
 *   <li><b>afterCommit 등록</b> — 트랜잭션 활성 상태에서 record 호출 시 즉시 save하지 않고 콜백만 등록한다.
 *       콜백을 수동으로 발화시켜 save가 그제서야 호출되는지 검증.</li>
 *   <li><b>트랜잭션 부재 시 즉시 save</b> — 동기화 비활성 상태에서는 record 호출 → 곧바로 save.</li>
 *   <li><b>save 실패 흡수</b> — repository.save가 예외를 던져도 호출자 호출이 정상 리턴.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminActionLogServiceTest {

    @Mock
    private AdminActionLogRepository repository;

    @Mock
    private TransactionTemplate requiresNewTemplate;

    @InjectMocks
    private AdminActionLogService service;

    private AdminActionContext context() {
        return new AdminActionContext(
                1L,
                AdminActionType.USER_SUSPEND,
                AdminTargetType.USER,
                100L,
                null,
                "ACTIVE",
                "SUSPENDED");
    }

    @AfterEach
    void clearTxSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    /** TransactionTemplate.executeWithoutResult가 호출되면 람다 본문을 즉시 실행하도록 stub. */
    private void stubTemplateToRunLambdaImmediately() {
        doAnswer(invocation -> {
            Consumer<org.springframework.transaction.TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(requiresNewTemplate).executeWithoutResult(any());
    }

    @Test
    @DisplayName("트랜잭션 활성 상태: record 호출 시 즉시 save X — afterCommit 콜백만 등록")
    void record_inActiveTransaction_doesNotSaveImmediately() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.record(context());

            // 즉시 save 호출 X
            verify(repository, never()).save(any());
            verify(requiresNewTemplate, never()).executeWithoutResult(any());

            // 콜백 등록 자체는 발생
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    @DisplayName("afterCommit 콜백 발화 시 REQUIRES_NEW 템플릿 + save 실행")
    void record_afterCommitCallback_invokesSaveInNewTx() {
        stubTemplateToRunLambdaImmediately();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.record(context());

            // 콜백 수동 발화
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(requiresNewTemplate).executeWithoutResult(any());
            verify(repository).save(any(AdminActionLog.class));
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    @DisplayName("트랜잭션 부재 상태: record 호출 시 즉시 save 실행")
    void record_withoutTransaction_savesImmediately() {
        stubTemplateToRunLambdaImmediately();

        service.record(context());

        verify(requiresNewTemplate).executeWithoutResult(any());
        verify(repository).save(any(AdminActionLog.class));
    }

    @Test
    @DisplayName("save 실패 흡수: repository.save 예외 발생해도 record 호출은 정상 리턴")
    void record_saveFailure_isAbsorbedSilently() {
        stubTemplateToRunLambdaImmediately();
        doThrow(new RuntimeException("DB connection lost")).when(repository).save(any());

        // 예외가 전파되지 않아야 함 — assertThatCode로 검증할 수도 있으나 단순 호출 후 통과 의미
        service.record(context());

        verify(repository).save(any(AdminActionLog.class));
    }

    @Test
    @DisplayName("template 자체가 예외 던져도 흡수")
    void record_templateFailure_isAbsorbedSilently() {
        doThrow(new RuntimeException("transaction manager broken"))
                .when(requiresNewTemplate)
                .executeWithoutResult(any());

        service.record(context());

        verify(repository, never()).save(any());
    }
}
