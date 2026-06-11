package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.jwt.JwtProperties;
import com.chunbaetour.domain.auth.jwt.RefreshClaims;
import com.chunbaetour.domain.auth.jwt.RefreshTokenStore;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.jwt.TokenWithId;
import com.chunbaetour.domain.common.audit.SecurityAuditLogger;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import com.chunbaetour.domain.report.entity.SanctionType;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ReissueService} 단위 테스트.
 *
 * <p>외부 의존(TokenIssuer/RefreshTokenStore/AccountRepository/JwtProperties)을 모두 mock해서
 * 분기별 ErrorCode 매핑과 정상 흐름 시 store.rotate 호출 인자를 검증한다.
 *
 * <p>PRD AC 커버리지:
 * <ul>
 *   <li>정상 재발급 → 새 토큰 쌍 + rotate 호출</li>
 *   <li>Refresh 만료 → AUTH_004</li>
 *   <li>Refresh 변조/Malformed → AUTH_005</li>
 *   <li>Redis 미존재(rotate=false) → AUTH_005</li>
 *   <li>정지 계정 → AUTH_012</li>
 *   <li>탈퇴된 사용자(DB 없음) → AUTH_005</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReissueServiceTest {

    private static final String REFRESH_TOKEN = "refresh.jwt.token";
    private static final long USER_ID = 42L;
    private static final String OLD_TID = "old-token-id";
    private static final String EMAIL = "user@example.com";
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private JwtProperties jwtProperties;

    /** KAN-105 감사 로그 mock. */
    @Mock
    private SecurityAuditLogger auditLogger;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-06-11T12:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private ReissueService reissueService;

    @Test
    void reissue_success_returns_new_pair_and_rotates_store() {
        Account active = accountWith(USER_ID, Role.USER, AccountStatus.ACTIVE);
        given(tokenIssuer.verifyRefresh(REFRESH_TOKEN)).willReturn(new RefreshClaims(USER_ID, OLD_TID));
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(active));
        given(tokenIssuer.issueAccess(USER_ID, Role.USER, EMAIL)).willReturn("new-access");
        given(tokenIssuer.issueRefresh(USER_ID)).willReturn(new TokenWithId("new-tid", "new-refresh"));
        given(jwtProperties.refreshTokenTtl()).willReturn(REFRESH_TTL);
        given(refreshTokenStore.rotate(USER_ID, OLD_TID, "new-tid", REFRESH_TTL)).willReturn(true);

        TokenPair pair = reissueService.reissue(REFRESH_TOKEN);

        assertThat(pair.accessToken()).isEqualTo("new-access");
        assertThat(pair.refreshToken()).isEqualTo("new-refresh");
        assertThat(pair.refreshTokenId()).isEqualTo("new-tid");
        assertThat(pair.role()).isEqualTo(Role.USER);

        // 정확한 인자로 rotate가 호출되어야 함 (CAS 원자성 보장의 진입점)
        verify(refreshTokenStore).rotate(USER_ID, OLD_TID, "new-tid", REFRESH_TTL);
    }

    @Test
    void reissue_with_expired_refresh_throws_AUTH_004() {
        // ExpiredJwtException은 JJWT가 exp claim 만료 시 던지는 전용 예외 → AUTH_004로 별도 매핑
        willThrow(new ExpiredJwtException(null, null, "expired"))
                .given(tokenIssuer).verifyRefresh(REFRESH_TOKEN);

        assertThatThrownBy(() -> reissueService.reissue(REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);

        // 검증 실패 단계에서 토큰 발급/회전이 일어나면 안 됨
        verify(tokenIssuer, never()).issueAccess(anyLong(), eq(Role.USER), anyString());
        verifyRefreshWasNotRotated();
    }

    @Test
    void reissue_with_tampered_refresh_throws_AUTH_005() {
        // 서명 오류/Malformed/잘못된 claim 등은 모두 AUTH_005로 통합 (사유 노출 최소화)
        willThrow(new MalformedJwtException("bad"))
                .given(tokenIssuer).verifyRefresh(REFRESH_TOKEN);

        assertThatThrownBy(() -> reissueService.reissue(REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    void reissue_when_user_deleted_throws_AUTH_005() {
        // soft-delete된 사용자는 findById에서 빈 Optional 반환 (@SQLRestriction 효과)
        given(tokenIssuer.verifyRefresh(REFRESH_TOKEN)).willReturn(new RefreshClaims(USER_ID, OLD_TID));
        given(accountRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reissueService.reissue(REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    void reissue_with_suspended_account_throws_AUTH_012() {
        // sanctionType=null (수동 정지) → isSystemSanctionExpired=false → 차단
        Account suspended = accountWith(USER_ID, Role.USER, AccountStatus.SUSPENDED);
        given(tokenIssuer.verifyRefresh(REFRESH_TOKEN)).willReturn(new RefreshClaims(USER_ID, OLD_TID));
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(suspended));

        assertThatThrownBy(() -> reissueService.reissue(REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        verifyRefreshWasNotRotated();
    }

    @Test
    void reissue_with_expired_system_sanction_clears_and_succeeds() {
        // sanctionEndAt = 1시간 전 (고정 clock 기준 만료) → clearSystemSanction 후 재발급 허용
        Account suspended = accountWith(USER_ID, Role.USER, AccountStatus.ACTIVE);
        suspended.applySystemSanction(SanctionType.SUSPEND_7D, LocalDateTime.of(2026, 6, 11, 11, 0, 0));

        given(tokenIssuer.verifyRefresh(REFRESH_TOKEN)).willReturn(new RefreshClaims(USER_ID, OLD_TID));
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(suspended));
        given(tokenIssuer.issueAccess(USER_ID, Role.USER, EMAIL)).willReturn("new-access");
        given(tokenIssuer.issueRefresh(USER_ID)).willReturn(new TokenWithId("new-tid", "new-refresh"));
        given(jwtProperties.refreshTokenTtl()).willReturn(REFRESH_TTL);
        given(refreshTokenStore.rotate(USER_ID, OLD_TID, "new-tid", REFRESH_TTL)).willReturn(true);

        TokenPair pair = reissueService.reissue(REFRESH_TOKEN);

        assertThat(pair.accessToken()).isEqualTo("new-access");
        assertThat(suspended.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(suspended.getSanctionType()).isNull();
    }

    @Test
    void reissue_when_rotate_returns_false_throws_AUTH_005() {
        // 이미 다른 reissue로 회전된 경우 또는 로그아웃되어 키 삭제된 경우
        // rotate가 false 반환 → CAS 실패 → AUTH_005
        Account active = accountWith(USER_ID, Role.USER, AccountStatus.ACTIVE);
        given(tokenIssuer.verifyRefresh(REFRESH_TOKEN)).willReturn(new RefreshClaims(USER_ID, OLD_TID));
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(active));
        given(tokenIssuer.issueAccess(USER_ID, Role.USER, EMAIL)).willReturn("new-access");
        given(tokenIssuer.issueRefresh(USER_ID)).willReturn(new TokenWithId("new-tid", "new-refresh"));
        given(jwtProperties.refreshTokenTtl()).willReturn(REFRESH_TTL);
        given(refreshTokenStore.rotate(USER_ID, OLD_TID, "new-tid", REFRESH_TTL)).willReturn(false);

        assertThatThrownBy(() -> reissueService.reissue(REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    private void verifyRefreshWasNotRotated() {
        verify(refreshTokenStore, never()).rotate(anyLong(), anyString(), anyString(), any(Duration.class));
    }

    /**
     * 헬퍼: 테스트용 Account 구성 (LoginServiceTest와 동일 패턴).
     */
    private static Account accountWith(long id, Role role, AccountStatus status) {
        Account account = Account.registerUser(EMAIL, "hashed", "tester");
        writeField(account, "id", id);
        writeField(account, "role", role);
        writeField(account, "status", status);
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
