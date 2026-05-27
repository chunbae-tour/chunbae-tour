package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.auth.event.UserRegisteredEvent;
import com.chunbaetour.domain.common.audit.SecurityAuditEventType;
import com.chunbaetour.domain.common.audit.SecurityAuditLogger;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class SignupService {

    /** KAN-104 메트릭 — 카탈로그({@code docs/operations/metrics-catalog.md}) 동기. */
    private static final String METRIC_SIGNUP_ATTEMPT = "auth.signup.attempt.total";

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final SecurityAuditLogger auditLogger;

    @Transactional
    public Account signup(SignupRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        String nickname = request.nickname();

        if (accountRepository.existsByEmail(email)) {
            meterRegistry.counter(METRIC_SIGNUP_ATTEMPT, "outcome", "email_dup").increment();
            auditLogger.emitFailure(SecurityAuditEventType.SIGNUP_FAILURE, null, ErrorCode.DUPLICATE_EMAIL.getCode(),
                    Map.of("reasonDetail", "email_dup"));
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (accountRepository.existsByNickname(nickname)) {
            meterRegistry.counter(METRIC_SIGNUP_ATTEMPT, "outcome", "nickname_dup").increment();
            auditLogger.emitFailure(SecurityAuditEventType.SIGNUP_FAILURE, null, ErrorCode.DUPLICATE_NICKNAME.getCode(),
                    Map.of("reasonDetail", "nickname_dup"));
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        String hashed = passwordHasher.hash(request.password());
        Account account = Account.registerUser(email, hashed, nickname);
        Account saved;
        try {
            saved = accountRepository.save(account);
            accountRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // race로 unique 충돌 — 동일 이메일/닉네임 재확인. 어떤 컬럼이 충돌했는지 메트릭에 분기 기록.
            if (accountRepository.existsByEmail(email)) {
                meterRegistry.counter(METRIC_SIGNUP_ATTEMPT, "outcome", "email_dup").increment();
                auditLogger.emitFailure(SecurityAuditEventType.SIGNUP_FAILURE, null, ErrorCode.DUPLICATE_EMAIL.getCode(),
                        Map.of("reasonDetail", "email_dup_race"));
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            if (accountRepository.existsByNickname(nickname)) {
                meterRegistry.counter(METRIC_SIGNUP_ATTEMPT, "outcome", "nickname_dup").increment();
                auditLogger.emitFailure(SecurityAuditEventType.SIGNUP_FAILURE, null, ErrorCode.DUPLICATE_NICKNAME.getCode(),
                        Map.of("reasonDetail", "nickname_dup_race"));
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            meterRegistry.counter(METRIC_SIGNUP_ATTEMPT, "outcome", "db_failure").increment();
            auditLogger.emitFailure(SecurityAuditEventType.SIGNUP_FAILURE, null, "DB_INTEGRITY_VIOLATION",
                    Map.of("reasonDetail", "data_integrity_violation"));
            throw e;
        }

        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId(), saved.getEmail(), saved.getNickname()));
        meterRegistry.counter(METRIC_SIGNUP_ATTEMPT, "outcome", "success").increment();
        // 트랜잭션 커밋 후에만 SIGNUP_SUCCESS 감사 emit — 커밋 실패 시 잘못된 success 로그 방지 (CR #1).
        // 비-트랜잭션 컨텍스트(테스트 등)에서는 즉시 emit으로 폴백.
        Long savedId = saved.getId();
        String role = saved.getRole().name();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    auditLogger.emitSuccess(SecurityAuditEventType.SIGNUP_SUCCESS, savedId,
                            Map.of("role", role));
                }
            });
        } else {
            auditLogger.emitSuccess(SecurityAuditEventType.SIGNUP_SUCCESS, savedId,
                    Map.of("role", role));
        }
        return saved;
    }
}
