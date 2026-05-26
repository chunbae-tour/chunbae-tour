package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.auth.event.UserRegisteredEvent;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupService {

    /** KAN-104 메트릭 — 카탈로그({@code docs/operations/metrics-catalog.md}) 동기. */
    private static final String METRIC_SIGNUP_ATTEMPT = "auth.signup.attempt.total";

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    @Transactional
    public Account signup(SignupRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        String nickname = request.nickname();

        if (accountRepository.existsByEmail(email)) {
            meterRegistry.counter(METRIC_SIGNUP_ATTEMPT, "outcome", "email_dup").increment();
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (accountRepository.existsByNickname(nickname)) {
            meterRegistry.counter(METRIC_SIGNUP_ATTEMPT, "outcome", "nickname_dup").increment();
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
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            if (accountRepository.existsByNickname(nickname)) {
                meterRegistry.counter(METRIC_SIGNUP_ATTEMPT, "outcome", "nickname_dup").increment();
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            meterRegistry.counter(METRIC_SIGNUP_ATTEMPT, "outcome", "db_failure").increment();
            throw e;
        }

        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId(), saved.getEmail(), saved.getNickname()));
        meterRegistry.counter(METRIC_SIGNUP_ATTEMPT, "outcome", "success").increment();
        return saved;
    }
}
