package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.auth.dto.UserMeResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserMeService} 단위 테스트.
 *
 * <p>PRD AC 커버리지:
 * <ul>
 *   <li>정상 조회 시 필드 매핑 정확</li>
 *   <li>Account 미존재(탈퇴 등) 시 AUTH_006 (재로그인 안내)</li>
 *   <li>DTO 변환 시 민감 필드(password) 누락 검증</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserMeServiceTest {

    private static final long USER_ID = 42L;
    private static final String EMAIL = "user@example.com";
    private static final String NICKNAME = "춘배유저";
    private static final String HASHED_PASSWORD = "hashed-bcrypt";

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private UserMeService userMeService;

    @Test
    void getMe_with_existing_user_returns_full_response() {
        Account account = activeUser(USER_ID, EMAIL, NICKNAME);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));

        UserMeResponse response = userMeService.getMe(USER_ID);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.nickname()).isEqualTo(NICKNAME);
        assertThat(response.role()).isEqualTo(Role.USER);
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.language()).isEqualTo("ko");
        assertThat(response.companionScore()).isEqualTo(0f);
        assertThat(response.companionReviewCount()).isEqualTo(0);
    }

    @Test
    void getMe_response_does_not_expose_password_field() {
        // 보안 회귀 방지: DTO record에 password가 절대 노출되지 않아야 함
        Account account = activeUser(USER_ID, EMAIL, NICKNAME);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));

        UserMeResponse response = userMeService.getMe(USER_ID);

        // record는 컴포넌트 메서드만 가짐. password() 메서드가 없으면 컴파일도 안 되므로 reflect로 확인
        boolean hasPasswordAccessor = java.util.Arrays.stream(UserMeResponse.class.getMethods())
                .anyMatch(m -> "password".equals(m.getName()) && m.getParameterCount() == 0);
        assertThat(hasPasswordAccessor)
                .as("UserMeResponse는 password 컴포넌트를 절대 가져선 안 됨 (보안)")
                .isFalse();
        assertThat(response).isNotNull();  // 단순 NPE 방어
    }

    @Test
    void getMe_with_nonexistent_user_throws_AUTH_006() {
        // 토큰은 유효하지만 사용자가 탈퇴 등으로 사라진 케이스 (@SQLRestriction 효과)
        given(accountRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userMeService.getMe(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    /**
     * 헬퍼: 영속 상태와 유사한 Account 생성. reflect로 id/createdAt 주입.
     * 실제 영속화 없이 매핑만 검증할 수 있게.
     */
    private static Account activeUser(long id, String email, String nickname) {
        Account account = Account.registerUser(email, HASHED_PASSWORD, nickname);
        writeField(account, "id", id);
        writeField(account, "createdAt", LocalDateTime.of(2026, 5, 19, 10, 0));
        return account;
    }

    private static void writeField(Account account, String name, Object value) {
        try {
            Field field = Account.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(account, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
