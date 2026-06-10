package com.chunbaetour.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.OauthProvider;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.OauthSignupRequest;
import com.chunbaetour.domain.auth.event.UserRegisteredEvent;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.jwt.TokenWithId;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link OauthSignupService} 단위 테스트 — 이메일 없는 가입 성공(길 A) end-to-end 흐름 + 전화 해시 중복 거부.
 * (영속성 레벨 email NULL insert는 {@code OauthNullEmailPersistenceIntegrationTest}가 검증.)
 */
@ExtendWith(MockitoExtension.class)
class OauthSignupServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private OauthSignupTicketIssuer ticketIssuer;
    @Mock private com.chunbaetour.domain.auth.jwt.TokenIssuer tokenIssuer;
    @Mock private com.chunbaetour.domain.auth.jwt.RefreshTokenStore refreshTokenStore;
    @Mock private com.chunbaetour.domain.auth.jwt.JwtProperties jwtProperties;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private com.chunbaetour.domain.common.audit.SecurityAuditLogger auditLogger;
    @Mock private PhoneHasher phoneHasher;

    @InjectMocks private OauthSignupService service;

    private OauthSignupRequest request(String nickname) {
        return new OauthSignupRequest("ticket", "홍길동", "010-1234-5678",
                LocalDate.of(1990, 1, 1), nickname);
    }

    @DisplayName("공급자 이메일 미제공(티켓 email=null) → 가입 성공(길 A) — null email 저장·토큰 발급·refresh/이벤트까지")
    @Test
    void signsUpSuccessfullyWithoutProviderEmail() {
        // 티켓에 공급자 이메일이 없음(예: 카카오 이메일 미동의) → AUTH_022 없이 끝까지 진행돼야 한다.
        when(ticketIssuer.verify("ticket"))
                .thenReturn(new OauthSignupTicket(OauthProvider.KAKAO, "oid-1", null));
        when(accountRepository.countByOauthIdentityIncludingDeleted("KAKAO", "oid-1")).thenReturn(0L);
        when(accountRepository.countByNicknameIncludingDeleted(anyString())).thenReturn(0L);
        when(phoneHasher.hash("01012345678")).thenReturn("phone-hash");
        when(accountRepository.countByPhoneHashIncludingDeleted("phone-hash")).thenReturn(0L);
        // save가 IDENTITY id를 채워 반환하는 동작 모사 — id는 토큰 발급/이벤트에 쓰인다.
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account account = inv.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 1L);
            return account;
        });
        when(tokenIssuer.issueAccess(eq(1L), eq(Role.USER), isNull())).thenReturn("access-token");
        when(tokenIssuer.issueRefresh(1L)).thenReturn(new TokenWithId("rtid", "refresh-token"));
        when(jwtProperties.refreshTokenTtl()).thenReturn(Duration.ofDays(7));

        TokenPair result = service.signup(request("소셜닉"));

        // 응답 토큰 — email 없는 계정도 정상 발급.
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.role()).isEqualTo(Role.USER);

        // 저장된 계정: email=null + oauth 식별자/암호화 대상 phone/해시가 정확히 들어갔는지.
        ArgumentCaptor<Account> savedCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(savedCaptor.capture());
        Account saved = savedCaptor.getValue();
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getOauthProvider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(saved.getOauthId()).isEqualTo("oid-1");
        assertThat(saved.getPhoneHash()).isEqualTo("phone-hash");
        assertThat(saved.getNickname()).isEqualTo("소셜닉");

        // email=null이 토큰 발급/가입 이벤트/refresh 저장까지 그대로 흘러도 깨지지 않는다.
        verify(tokenIssuer).issueAccess(eq(1L), eq(Role.USER), isNull());
        verify(eventPublisher).publishEvent(new UserRegisteredEvent(1L, null, "소셜닉"));
        // 단위 테스트는 비-트랜잭션 컨텍스트 → afterCommit 폴백(즉시 실행) 경로로 refresh 저장 검증.
        verify(refreshTokenStore).save(1L, "rtid", Duration.ofDays(7));
    }

    @DisplayName("전화번호 해시 중복 → DUPLICATE_PHONE (원문 아닌 phone_hash로 판별, email null이어도 게이트 통과)")
    @Test
    void rejectsDuplicatePhoneByHash() {
        when(ticketIssuer.verify("ticket"))
                .thenReturn(new OauthSignupTicket(OauthProvider.KAKAO, "oid-2", "user@kakao.com"));
        when(accountRepository.countByOauthIdentityIncludingDeleted("KAKAO", "oid-2")).thenReturn(0L);
        when(accountRepository.countByEmailIncludingDeleted("user@kakao.com")).thenReturn(0L);
        when(accountRepository.countByNicknameIncludingDeleted(anyString())).thenReturn(0L);
        when(phoneHasher.hash("01012345678")).thenReturn("phone-hash");
        when(accountRepository.countByPhoneHashIncludingDeleted("phone-hash")).thenReturn(1L);

        assertThatThrownBy(() -> service.signup(request("닉네임")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PHONE);
    }

    @DisplayName("공급자 이메일에 앞뒤 공백 → trim 후 lower 정규화로 저장/중복검사")
    @Test
    void normalizesEmailWithTrimThenLower() {
        when(ticketIssuer.verify("ticket"))
                .thenReturn(new OauthSignupTicket(OauthProvider.KAKAO, "oid-3", "  User@Kakao.com "));
        when(accountRepository.countByOauthIdentityIncludingDeleted("KAKAO", "oid-3")).thenReturn(0L);
        // 정규화된 값으로 중복검사해야 한다 — 여기서 중복 처리해 흐름 종료(정규화 검증이 목적).
        when(accountRepository.countByEmailIncludingDeleted("user@kakao.com")).thenReturn(1L);

        assertThatThrownBy(() -> service.signup(request("닉네임")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }
}
