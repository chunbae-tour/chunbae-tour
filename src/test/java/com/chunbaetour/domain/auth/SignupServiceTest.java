package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.auth.event.UserRegisteredEvent;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    /** KAN-104 메트릭 in-memory registry. */
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private SignupService signupService;

    private SignupRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new SignupRequest(
                "user@example.com",
                "Pa$$w0rd1!",
                "춘배유저"
        );
    }

    @Test
    void signup_success_returns_saved_account_and_publishes_event() {
        given(accountRepository.existsByEmail(validRequest.email())).willReturn(false);
        given(accountRepository.existsByNickname(validRequest.nickname())).willReturn(false);
        given(passwordHasher.hash(validRequest.password())).willReturn("hashed");
        given(accountRepository.save(any(Account.class))).willAnswer(invocation -> invocation.getArgument(0));

        Account saved = signupService.signup(validRequest);

        assertThat(saved.getEmail()).isEqualTo(validRequest.email());
        assertThat(saved.getNickname()).isEqualTo(validRequest.nickname());
        assertThat(saved.getPassword()).isEqualTo("hashed");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(eventPublisher).publishEvent(any(UserRegisteredEvent.class));
        assertThat(meterRegistry.counter("auth.signup.attempt.total", "outcome", "success").count())
                .isEqualTo(1.0);
    }

    @Test
    void signup_with_duplicate_email_throws_AUTH_008() {
        given(accountRepository.existsByEmail(validRequest.email())).willReturn(true);

        assertThatThrownBy(() -> signupService.signup(validRequest))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);

        assertThat(meterRegistry.counter("auth.signup.attempt.total", "outcome", "email_dup").count())
                .isEqualTo(1.0);
    }

    @Test
    void signup_with_duplicate_nickname_throws_AUTH_009() {
        given(accountRepository.existsByEmail(validRequest.email())).willReturn(false);
        given(accountRepository.existsByNickname(validRequest.nickname())).willReturn(true);

        assertThatThrownBy(() -> signupService.signup(validRequest))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);

        assertThat(meterRegistry.counter("auth.signup.attempt.total", "outcome", "nickname_dup").count())
                .isEqualTo(1.0);
    }
}
