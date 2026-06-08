package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.jwt.JwtProperties;
import com.chunbaetour.domain.auth.jwt.RefreshTokenStore;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.jwt.TokenWithId;
import com.chunbaetour.domain.common.audit.SecurityAuditEventType;
import com.chunbaetour.domain.common.audit.SecurityAuditLogger;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 로그인 흐름.
 *
 * <p>S2에서는 Access + Refresh를 모두 Body로 반환했지만, S3부터는:
 * <ul>
 *   <li>Access Token: Body로 반환 (클라이언트가 메모리 보관, Authorization 헤더로 전송)</li>
 *   <li>Refresh Token: HttpOnly Cookie로 반환 (XSS 방어) + Redis 저장 (서버 측 무효화)</li>
 * </ul>
 * 본 서비스는 {@link RefreshTokenStore#save}까지만 책임진다. Cookie 발급은 컨트롤러가 처리.
 *
 * <p>실패 분기 (모두 보안 응답: 사유 노출 최소화):
 * <ul>
 *   <li>이메일 없음 / 비밀번호 불일치 → {@link ErrorCode#LOGIN_FAILED} (AUTH_001) — 이메일 존재 여부 노출 금지</li>
 *   <li>정지 계정 → {@link ErrorCode#ACCOUNT_SUSPENDED} (AUTH_012)</li>
 *   <li>요청 page의 role 미스매치 → {@link ErrorCode#ACCESS_DENIED} (AUTH_007)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    /** KAN-104 메트릭 — 카탈로그({@code docs/operations/metrics-catalog.md}) 동기. outcome 라벨 분기. */
    private static final String METRIC_LOGIN_ATTEMPT = "auth.login.attempt.total";

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProperties jwtProperties;
    private final MeterRegistry meterRegistry;
    private final SecurityAuditLogger auditLogger;

    /**
     * 로그인 처리. 성공 시 TokenPair 반환 + Redis에 Refresh 저장.
     *
     * <p>{@code readOnly=true} 트랜잭션: 본 흐름은 Account 조회만 하므로 readOnly로 두어 1차 캐시 비활성화와
     * 옵티마이저 힌트를 받는다. Redis 호출은 트랜잭션 밖이므로 readOnly와 무관.
     *
     * @param loginId      입력된 이메일 (대소문자 무관)
     * @param password     입력된 평문 비밀번호 (BCrypt 비교 후 메모리에서 즉시 GC 대상)
     * @param requiredRole 호출한 endpoint가 요구하는 role. S2~S3은 USER, S5에서 MERCHANT/ADMIN 추가 예정
     * @return 발급된 토큰 쌍 (Cookie 직렬화는 컨트롤러 책임)
     */
    @Transactional(readOnly = true)
    public TokenPair login(String loginId, String password, Role requiredRole) {
        // 이메일은 대소문자 무관 저장 정책이므로 조회 직전 lowercase 정규화
        String email = loginId.toLowerCase(Locale.ROOT);

        Account account = accountRepository.findByEmail(email).orElse(null);
        if (account == null) {
            // 이메일 미존재 — 비밀번호 불일치와 같은 응답으로 정보 노출 차단.
            // 메트릭에서는 분기 구분 (`email_not_found` vs `invalid_password`)이 의미 있을 수 있으나,
            // 외부 응답이 동일하므로 outcome도 통일해 운영 대시보드 노이즈 최소화.
            meterRegistry.counter(METRIC_LOGIN_ATTEMPT, "outcome", "invalid_password").increment();
            // KAN-105: 감사 로그 — actorId null (이메일 미존재 = 추정 brute force).
            auditLogger.emitFailure(SecurityAuditEventType.LOGIN_FAILURE, null, ErrorCode.LOGIN_FAILED.getCode(),
                    Map.of("requiredRole", requiredRole.name(), "reasonDetail", "email_not_found"));
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 비밀번호 검증 (BCrypt). 일치 여부만 노출, 어느 단계에서 실패했는지는 노출 안 함 (timing attack 외 사유 노출 차단)
        if (!passwordHasher.matches(password, account.getPassword())) {
            meterRegistry.counter(METRIC_LOGIN_ATTEMPT, "outcome", "invalid_password").increment();
            auditLogger.emitFailure(SecurityAuditEventType.LOGIN_FAILURE, account.getId(), ErrorCode.LOGIN_FAILED.getCode(),
                    Map.of("requiredRole", requiredRole.name(), "reasonDetail", "wrong_password"));
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 요청한 endpoint가 USER용인데 ADMIN 계정으로 로그인 시도 → 거부.
        // role mismatch 자체로 정보 노출되지만, 엔드포인트 분리가 곧 보안 모델이라 허용.
        // SUSPENDED 체크보다 먼저 두는 이유: 다른 role 계정의 정지 상태가 page 호출자에게 누설되는 oracle 차단.
        if (account.getRole() != requiredRole) {
            meterRegistry.counter(METRIC_LOGIN_ATTEMPT, "outcome", "role_mismatch").increment();
            auditLogger.emitFailure(SecurityAuditEventType.LOGIN_FAILURE, account.getId(), ErrorCode.ACCESS_DENIED.getCode(),
                    Map.of("requiredRole", requiredRole.name(),
                            "actualRole", account.getRole().name(),
                            "reasonDetail", "role_mismatch"));
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 정지 계정은 비밀번호와 무관하게 차단. 메시지는 명시 (사용자에게 정지 사실 안내 필요).
        // role 체크를 먼저 통과한 후 검사 — 다른 page 호출자에게 정지 상태가 노출되는 oracle 차단.
        if (account.getStatus() == AccountStatus.SUSPENDED) {
            meterRegistry.counter(METRIC_LOGIN_ATTEMPT, "outcome", "suspended").increment();
            auditLogger.emitFailure(SecurityAuditEventType.LOGIN_FAILURE, account.getId(), ErrorCode.ACCOUNT_SUSPENDED.getCode(),
                    Map.of("requiredRole", requiredRole.name(), "reasonDetail", "account_suspended"));
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        // 토큰 발급. Refresh는 매번 새로운 tokenId(UUID)를 가진다.
        String accessToken = tokenIssuer.issueAccess(account.getId(), account.getRole(), account.getEmail());
        TokenWithId refresh = tokenIssuer.issueRefresh(account.getId());

        // Redis 저장: 같은 사용자의 기존 Refresh를 덮어쓴다 (한 사용자 = 한 세션 모델).
        // 다른 디바이스에서 동일 계정으로 로그인하면 이전 디바이스의 Refresh가 즉시 무효화된다.
        refreshTokenStore.save(account.getId(), refresh.tokenId(), jwtProperties.refreshTokenTtl());

        meterRegistry.counter(METRIC_LOGIN_ATTEMPT, "outcome", "success").increment();
        auditLogger.emitSuccess(SecurityAuditEventType.LOGIN_SUCCESS, account.getId(),
                Map.of("role", account.getRole().name()));
        return new TokenPair(accessToken, refresh.token(), refresh.tokenId(), account.getRole());
    }
}
