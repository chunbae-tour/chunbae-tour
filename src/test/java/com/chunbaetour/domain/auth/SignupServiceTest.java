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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
    }

    @Test
    void signup_with_duplicate_email_throws_AUTH_008() {
        given(accountRepository.existsByEmail(validRequest.email())).willReturn(true);

        assertThatThrownBy(() -> signupService.signup(validRequest))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void signup_with_duplicate_nickname_throws_AUTH_009() {
        given(accountRepository.existsByEmail(validRequest.email())).willReturn(false);
        given(accountRepository.existsByNickname(validRequest.nickname())).willReturn(true);

        assertThatThrownBy(() -> signupService.signup(validRequest))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
    }
}
