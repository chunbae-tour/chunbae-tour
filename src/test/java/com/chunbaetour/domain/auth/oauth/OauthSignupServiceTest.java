package com.chunbaetour.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.OauthProvider;
import com.chunbaetour.domain.auth.dto.OauthSignupRequest;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link OauthSignupService} 단위 테스트 — 이메일 선점 방어(공급자 미제공 거부) + 전화 해시 중복 거부.
 * (성공 경로는 트랜잭션/afterCommit 의존이 커 통합테스트 영역으로 둠.)
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

    @DisplayName("공급자가 이메일 미제공(티켓 email=null) → OAUTH_EMAIL_NOT_PROVIDED (임의 이메일 선점 차단)")
    @Test
    void rejectsWhenProviderEmailMissing() {
        // 티켓에 공급자 이메일이 없음(예: 카카오 이메일 미동의).
        when(ticketIssuer.verify("ticket"))
                .thenReturn(new OauthSignupTicket(OauthProvider.KAKAO, "oid-1", null));
        when(accountRepository.countByOauthIdentityIncludingDeleted("KAKAO", "oid-1")).thenReturn(0L);

        assertThatThrownBy(() -> service.signup(request("닉네임")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_EMAIL_NOT_PROVIDED);
    }

    @DisplayName("전화번호 해시 중복 → DUPLICATE_PHONE (원문 아닌 phone_hash로 판별)")
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
}
