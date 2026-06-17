package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.dto.PatchUserMeRequest;
import com.chunbaetour.domain.auth.dto.UserMeResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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

    @Mock
    private com.chunbaetour.domain.auth.profileimage.ProfileImageService profileImageService;

    @InjectMocks
    private UserMeService userMeService;

    @org.junit.jupiter.api.BeforeEach
    void stubProfileResolver() {
        // 표시 변환은 passthrough로 — 기존 단위 테스트의 profileImageUrl 가정 유지(KAN-320)
        org.mockito.Mockito.lenient()
                .when(profileImageService.toDisplayUrl(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

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
        // 표시 URL이 ProfileImageService.toDisplayUrl을 거쳐 매핑되는지(변환 우회 회귀 감지, KAN-320)
        assertThat(response.profileImageUrl()).isEqualTo(account.getProfileImageUrl()); // 본 테스트는 identity stub
        then(profileImageService).should().toDisplayUrl(account.getProfileImageUrl());
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

    // ===== KAN-127 Epic A S2 — PATCH /users/me =====

    @Test
    void updateMe_with_all_fields_null_returns_current_state_without_db_write() {
        // PATCH partial update — 모든 필드 null이면 noop. 도메인 변경 없음 + 응답은 GET /me와 동일.
        Account account = activeUser(USER_ID, EMAIL, NICKNAME);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));

        UserMeResponse response = userMeService.updateMe(USER_ID, new PatchUserMeRequest(null, null, null));

        assertThat(response.nickname()).isEqualTo(NICKNAME);
        // 중복 체크는 nickname null이면 호출되지 않아야 (불필요한 query 회피)
        verify(accountRepository, never()).existsByNicknameAndIdNot(any(String.class), any(Long.class));
        // noop 분기에서는 saveAndFlush가 발생하면 안 됨 (DB write 회피 회귀 가드)
        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    void updateMe_with_only_nickname_updates_nickname_and_keeps_others() {
        Account account = activeUser(USER_ID, EMAIL, NICKNAME);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        given(accountRepository.existsByNicknameAndIdNot("새닉네임", USER_ID)).willReturn(false);

        UserMeResponse response = userMeService.updateMe(
                USER_ID, new PatchUserMeRequest("새닉네임", null, null));

        // nickname만 갱신
        assertThat(response.nickname()).isEqualTo("새닉네임");
        // language는 변경 안 됨 (Account 기본 "ko" 유지)
        assertThat(response.language()).isEqualTo("ko");
        // profileImageUrl도 변경 안 됨
        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    void updateMe_with_all_fields_updates_all() {
        Account account = activeUser(USER_ID, EMAIL, NICKNAME);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        given(accountRepository.existsByNicknameAndIdNot("새닉네임", USER_ID)).willReturn(false);

        UserMeResponse response = userMeService.updateMe(
                USER_ID, new PatchUserMeRequest("새닉네임", "en", "https://example.com/avatar.png"));

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(response.language()).isEqualTo("en");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    void updateMe_with_duplicate_nickname_throws_AUTH_009() {
        Account account = activeUser(USER_ID, EMAIL, NICKNAME);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        // 다른 사용자가 이미 점유 중
        given(accountRepository.existsByNicknameAndIdNot("중복닉네임", USER_ID)).willReturn(true);

        assertThatThrownBy(() -> userMeService.updateMe(
                USER_ID, new PatchUserMeRequest("중복닉네임", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);

        // 본인 nickname은 변경되지 않아야 함 (도메인 메서드 호출 전 차단)
        assertThat(account.getNickname()).isEqualTo(NICKNAME);
    }

    @Test
    void updateMe_with_same_own_nickname_does_NOT_trigger_duplicate_error() {
        // 본인이 자기 자신 nickname 그대로 보내도 본인 제외 중복 체크가 false → 정상 통과.
        Account account = activeUser(USER_ID, EMAIL, NICKNAME);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        given(accountRepository.existsByNicknameAndIdNot(NICKNAME, USER_ID)).willReturn(false);

        UserMeResponse response = userMeService.updateMe(
                USER_ID, new PatchUserMeRequest(NICKNAME, null, null));

        assertThat(response.nickname()).isEqualTo(NICKNAME);
    }

    // 의도된 동작 — UserMeService Javadoc 참조.
    // 탈퇴자가 토큰만 들고 호출한 케이스를 "탈퇴 사실 노출하지 않음" 보안 원칙에 따라 AUTH_006(인증 요구)으로 응답.
    // USER_NOT_FOUND를 쓰면 "이 user_id가 탈퇴했다"는 정보가 응답 차이로 추론됨.
    @Test
    void updateMe_with_nonexistent_user_throws_AUTH_006() {
        given(accountRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userMeService.updateMe(
                USER_ID, new PatchUserMeRequest("새닉네임", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    @DisplayName("existsByNicknameAndIdNot 통과 후 saveAndFlush에서 DataIntegrityViolation 발생 시 AUTH_009로 변환")
    void updateMe_with_race_data_integrity_violation_throws_DUPLICATE_NICKNAME() {
        // 동시성 race: pre-check는 통과했지만 flush 시점에 UK 충돌 발생 → AUTH_009 매핑
        Account account = activeUser(USER_ID, EMAIL, NICKNAME);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        given(accountRepository.existsByNicknameAndIdNot("새닉네임", USER_ID)).willReturn(false);
        given(accountRepository.saveAndFlush(any(Account.class)))
                .willThrow(new DataIntegrityViolationException("uk_account_nickname"));

        PatchUserMeRequest request = new PatchUserMeRequest("새닉네임", null, null);

        assertThatThrownBy(() -> userMeService.updateMe(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
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
