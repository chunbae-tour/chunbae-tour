package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.auth.dto.UserMeHomeResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserMeHomeService} 단위 테스트 (KAN-128 Epic A S3).
 *
 * <p>PRD AC 커버리지:
 * <ul>
 *   <li>정상 조회 시 profile + wallet 통합 응답</li>
 *   <li>wallet 없는 사용자도 balance=0 fallback (회원가입 이벤트 race 방어)</li>
 *   <li>Account 미존재 시 AUTH_006</li>
 *   <li>응답의 profile 필드가 KAN-63 GET /me와 동일 (스키마 일관성)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserMeHomeServiceTest {

    private static final long USER_ID = 42L;
    private static final String EMAIL = "user@example.com";
    private static final String NICKNAME = "춘배유저";
    private static final String HASHED_PASSWORD = "hashed-bcrypt";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private UserMeHomeService userMeHomeService;

    @Test
    void getHome_with_account_and_wallet_returns_combined_response() {
        Account account = activeUser(USER_ID, EMAIL, NICKNAME);
        Wallet wallet = walletWithBalance(USER_ID, 10_000L);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));

        UserMeHomeResponse response = userMeHomeService.getHome(USER_ID);

        // profile은 KAN-63 응답 포맷 재사용
        assertThat(response.profile().userId()).isEqualTo(USER_ID);
        assertThat(response.profile().email()).isEqualTo(EMAIL);
        assertThat(response.profile().nickname()).isEqualTo(NICKNAME);
        // wallet
        assertThat(response.wallet().balance()).isEqualTo(10_000L);
    }

    @Test
    void getHome_without_wallet_returns_balance_zero_fallback() {
        // 회원가입 후 wallet 생성 이벤트 race로 wallet 미생성 가능 — fallback 검증
        Account account = activeUser(USER_ID, EMAIL, NICKNAME);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        UserMeHomeResponse response = userMeHomeService.getHome(USER_ID);

        assertThat(response.profile().userId()).isEqualTo(USER_ID);
        assertThat(response.wallet().balance()).isEqualTo(0L);
    }

    @Test
    void getHome_with_nonexistent_user_throws_AUTH_006() {
        given(accountRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userMeHomeService.getHome(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    void getHome_response_profile_does_not_expose_password() {
        // 보안 회귀 가드: UserMeResponse가 password 컴포넌트를 절대 가져선 안 됨
        boolean hasPasswordAccessor = java.util.Arrays.stream(
                com.chunbaetour.domain.auth.dto.UserMeResponse.class.getMethods())
                .anyMatch(m -> "password".equals(m.getName()) && m.getParameterCount() == 0);
        assertThat(hasPasswordAccessor)
                .as("UserMeResponse는 password 컴포넌트를 절대 가져선 안 됨 (보안)")
                .isFalse();
    }

    private static Account activeUser(long id, String email, String nickname) {
        Account account = Account.registerUser(email, HASHED_PASSWORD, nickname);
        writeField(Account.class, account, "id", id);
        writeField(Account.class, account, "createdAt", LocalDateTime.of(2026, 5, 26, 10, 0));
        return account;
    }

    private static Wallet walletWithBalance(long userId, long balance) {
        Wallet wallet = Wallet.create(userId);
        writeField(Wallet.class, wallet, "balance", balance);
        return wallet;
    }

    private static <T> void writeField(Class<T> clazz, T target, String name, Object value) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
