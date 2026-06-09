package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.dto.OauthSignupRequest;
import com.chunbaetour.domain.auth.event.UserRegisteredEvent;
import com.chunbaetour.domain.auth.jwt.JwtProperties;
import com.chunbaetour.domain.auth.jwt.RefreshTokenStore;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.jwt.TokenWithId;
import com.chunbaetour.domain.common.audit.SecurityAuditEventType;
import com.chunbaetour.domain.common.audit.SecurityAuditLogger;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 소셜 로그인 2단계 — 가입 티켓 검증 후 추가정보(이름/전화/생년월일/이메일/닉네임)로 계정 생성 + JWT 발급.
 *
 * <p>중복 방지: 전화번호(숫자 정규화 후 UNIQUE) + 이메일 + 닉네임 + (provider, oauthId). DB UNIQUE 제약과
 * 사전 검사를 함께 둬, race로 UNIQUE 위반이 나면 어느 컬럼인지 재검사해 정확한 에러코드로 변환한다.
 */
@Service
@RequiredArgsConstructor
public class OauthSignupService {

    private final AccountRepository accountRepository;
    private final OauthSignupTicketIssuer ticketIssuer;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProperties jwtProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityAuditLogger auditLogger;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public TokenPair signup(OauthSignupRequest request) {
        OauthSignupTicket ticket = ticketIssuer.verify(request.ticket());

        // 티켓이 가리키는 소셜 계정이 이미 가입돼 있으면(동시 가입 등) 거부.
        if (accountRepository.countByOauthIdentityIncludingDeleted(ticket.provider().name(), ticket.oauthId()) > 0) {
            throw new BusinessException(ErrorCode.OAUTH_ALREADY_REGISTERED);
        }

        String email = request.email().toLowerCase(Locale.ROOT);
        String phone = normalizePhone(request.phone());
        String nickname = request.nickname();

        if (accountRepository.countByEmailIncludingDeleted(email) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (accountRepository.countByNicknameIncludingDeleted(nickname) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        if (accountRepository.countByPhoneIncludingDeleted(phone) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE);
        }

        Account account = Account.registerOauthUser(
                ticket.provider(), ticket.oauthId(), email, nickname,
                request.name(), phone, request.birthdate());

        Account saved;
        try {
            saved = accountRepository.save(account);
            accountRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // flush 실패한 엔티티를 세션에서 분리(후속 query auto-flush 시 AssertionFailure 회피) — SignupService와 동일.
            entityManager.clear();
            if (accountRepository.countByEmailIncludingDeleted(email) > 0) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            if (accountRepository.existsByNickname(nickname)) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            // 사전 검사와 동일하게 탈퇴(soft-delete) row까지 보는 *IncludingDeleted로 재검사 — existsByPhone/
            // findByOauthProviderAndOauthId는 @SQLRestriction(deleted_at IS NULL)이라 탈퇴자가 점유한 UNIQUE
            // 충돌을 못 봐 원래 예외가 500으로 새어나갔다 (CR 반영).
            if (accountRepository.countByPhoneIncludingDeleted(phone) > 0) {
                throw new BusinessException(ErrorCode.DUPLICATE_PHONE);
            }
            if (accountRepository.countByOauthIdentityIncludingDeleted(ticket.provider().name(), ticket.oauthId()) > 0) {
                throw new BusinessException(ErrorCode.OAUTH_ALREADY_REGISTERED);
            }
            throw e;
        }

        eventPublisher.publishEvent(
                new UserRegisteredEvent(saved.getId(), saved.getEmail(), saved.getNickname()));

        // 토큰 생성(JWT 서명)은 순수 연산이라 트랜잭션 안에서 해도 무방하나, Redis refresh 저장 + 성공 감사로그는
        // 외부 side-effect라 커밋 이후로 미룬다 — 커밋이 실패해 계정이 롤백되면 (1) DB엔 없는 계정의 refresh가
        // Redis에 남는 orphan, (2) 잘못된 SIGNUP_SUCCESS 로그가 생기는 것을 막는다 (hyeonmin02 리뷰 반영, email
        // SignupService의 afterCommit 철학과 동일). afterCommit은 프록시 커밋 완료 중 실행되므로 컨트롤러가
        // TokenPair를 받는 시점엔 refresh 저장이 끝나 있다. 비-트랜잭션 컨텍스트(테스트)는 즉시 실행으로 폴백.
        Long savedId = saved.getId();
        Map<String, String> auditMetadata =
                Map.of("method", "oauth", "provider", ticket.provider().name(), "role", saved.getRole().name());
        String accessToken = tokenIssuer.issueAccess(saved.getId(), saved.getRole(), saved.getEmail());
        TokenWithId refresh = tokenIssuer.issueRefresh(saved.getId());

        // 커밋 후에만 실행: refresh 저장(orphan 방지) + 성공 감사로그(false success 방지).
        Runnable afterCommitActions = () -> {
            refreshTokenStore.save(savedId, refresh.tokenId(), jwtProperties.refreshTokenTtl());
            auditLogger.emitSuccess(SecurityAuditEventType.SIGNUP_SUCCESS, savedId, auditMetadata);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    afterCommitActions.run();
                }
            });
        } else {
            afterCommitActions.run();
        }

        return new TokenPair(accessToken, refresh.token(), refresh.tokenId(), saved.getRole());
    }

    /** 하이픈 등 비숫자 제거 — 중복판별/저장 일관성. */
    private String normalizePhone(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }
}
