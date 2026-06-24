package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.jwt.JwtProperties;
import com.chunbaetour.domain.auth.jwt.RefreshClaims;
import com.chunbaetour.domain.auth.jwt.RefreshTokenStore;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.jwt.TokenWithId;
import com.chunbaetour.domain.common.audit.SecurityAuditEventType;
import com.chunbaetour.domain.common.audit.SecurityAuditLogger;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token Rotation 기반 Access Token 재발급 흐름.
 *
 * <p>핵심 책임:
 * <ol>
 *   <li>입력된 Refresh JWT를 검증 (서명, 만료, 타입)</li>
 *   <li>Redis에 저장된 "현재 유효한 tokenId"와 일치하는지 검증</li>
 *   <li>사용자가 여전히 활성 상태인지 검증 (탈퇴/정지 차단)</li>
 *   <li>새 Access + 새 Refresh 발급</li>
 *   <li>Redis에서 old tokenId → new tokenId로 원자 회전 (CAS 실패 시 거부)</li>
 * </ol>
 *
 * <p>실패 분기 (보안상 사유 노출 최소화):
 * <ul>
 *   <li>Refresh 만료(exp claim 기준) → {@link ErrorCode#REFRESH_TOKEN_EXPIRED} (AUTH_004) — 클라이언트는 재로그인 안내</li>
 *   <li>서명 오류/변조 → {@link ErrorCode#REFRESH_TOKEN_INVALID} (AUTH_005)</li>
 *   <li>Redis 미존재 (이미 회전됨/로그아웃됨) → AUTH_005</li>
 *   <li>탈퇴된 사용자 (DB에 없음) → AUTH_005 (탈퇴 노출 차단)</li>
 *   <li>정지 계정 → {@link ErrorCode#ACCOUNT_SUSPENDED} (AUTH_012) — 명시적 안내</li>
 *   <li>동시 reissue로 인한 CAS 실패 → AUTH_005 + Refresh 계열 무효화 (탈취 의심: 정상/공격자 모두 재로그인 강제)</li>
 * </ul>
 *
 * <p>설계 결정: Refresh claim에 role/email 포함하지 않은 이유는 권한 변경(USER → MERCHANT 승격)
 * 시점 이후의 reissue가 stale role을 발급하지 않도록 매번 DB에서 최신 role/email을 조회하기 위함.
 */
@Service
@RequiredArgsConstructor
public class ReissueService {

    private final TokenIssuer tokenIssuer;
    private final RefreshTokenStore refreshTokenStore;
    private final AccountRepository accountRepository;
    private final JwtProperties jwtProperties;
    private final SecurityAuditLogger auditLogger;
    private final Clock clock;

    /**
     * Refresh Token으로 새 Access + Refresh를 발급한다.
     *
     * <p>{@code @Transactional}: 시스템 제재 만료 시 clearSystemSanction() 호출로 Account 상태 변경 필요.
     * Redis 호출(rotate)은 트랜잭션 밖이므로 영향 없음.
     *
     * @param refreshToken Cookie에서 추출한 Refresh JWT
     * @return 새 토큰 쌍. Access는 Body로, Refresh는 Cookie로 전달 (컨트롤러 책임)
     */
    @Transactional
    public TokenPair reissue(String refreshToken) {
        // 1. JWT 검증 — 만료/변조 분기
        RefreshClaims claims = verifyOrThrow(refreshToken);

        long userId = claims.userId();
        String oldTokenId = claims.tokenId();

        // 2. 사용자 조회 — soft-delete 적용 (@SQLRestriction)이므로 탈퇴자는 자동 제외
        Account account = accountRepository.findById(userId).orElse(null);
        if (account == null) {
            auditLogger.emitFailure(SecurityAuditEventType.REFRESH_REJECTED, userId,
                    ErrorCode.REFRESH_TOKEN_INVALID.getCode(),
                    Map.of("reasonDetail", "account_not_found"));
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 3. 정지 계정 차단 — 만료 여부 먼저 체크, 만료 시 즉시 해제 후 재발급 허용
        if (account.getStatus() == AccountStatus.SUSPENDED) {
            if (account.isSystemSanctionExpired(LocalDateTime.now(clock))) {
                account.clearSystemSanction();
            } else {
                auditLogger.emitFailure(SecurityAuditEventType.REFRESH_REJECTED, userId,
                        ErrorCode.ACCOUNT_SUSPENDED.getCode(),
                        Map.of("reasonDetail", "account_suspended"));
                throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
            }
        }

        // 4. 새 토큰 발급 — DB에서 가져온 최신 role/email 사용 (권한 변경 즉시 반영)
        String newAccess = tokenIssuer.issueAccess(account.getId(), account.getRole(), account.getEmail());
        TokenWithId newRefresh = tokenIssuer.issueRefresh(account.getId());

        // 5. Redis CAS 회전 — 동시 reissue 중 한쪽만 성공.
        //    실패 케이스:
        //    - Redis 키 없음 (로그아웃되어 삭제됨)
        //    - 다른 tokenId가 저장돼 있음 (이미 다른 reissue가 회전 완료 → 이 요청은 stale 토큰)
        //    - 탈취된 Refresh로 거의 동시에 요청 시 한쪽만 통과
        boolean rotated = refreshTokenStore.rotate(
                account.getId(),
                oldTokenId,
                newRefresh.tokenId(),
                jwtProperties.refreshTokenTtl()
        );
        if (!rotated) {
            // CAS 실패 = 탈취 의심 또는 동시 reissue race. KAN-105: 별도 감사 분기.
            auditLogger.emitFailure(SecurityAuditEventType.REFRESH_REJECTED, userId,
                    ErrorCode.REFRESH_TOKEN_INVALID.getCode(),
                    Map.of("reasonDetail", "cas_failure"));
            // 토큰 계열 무효화 — race에서 이긴 쪽(이미 회전을 마친 측)의 Refresh 키까지 삭제해 "둘 다 차단".
            //   삭제 후엔 현재 Redis에 남아 있던 tokenId(이긴 쪽이 받은 새 Refresh)로도 CAS가 nil과 비교돼 실패 →
            //   정상 사용자·공격자 모두 다음 reissue 불가 → 재로그인 강제(탈취 Refresh 수명을 체인에서 즉시 차단).
            //   throw만 하던 기존 동작은 race에서 진 쪽만 막혀 이긴 쪽(탈취 의심) Refresh가 생존하는 갭이 있었음.
            //   Access 토큰은 jti 단건 blacklist만 있고 이 경로엔 access jti가 없어 즉시 무효화 불가 →
            //   잔여 TTL(최대 PT30M) 자연 만료로 닫힌다(피해 시간 bounded). Redis 실패는 LogoutService와 동일하게 전파.
            refreshTokenStore.delete(account.getId());
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        auditLogger.emitSuccess(SecurityAuditEventType.REFRESH_ROTATED, account.getId(),
                Map.of("role", account.getRole().name()));
        return new TokenPair(newAccess, newRefresh.token(), newRefresh.tokenId(), account.getRole());
    }

    /**
     * Refresh JWT 검증 + 예외 변환.
     *
     * <p>JJWT의 raw 예외를 ErrorCode 매핑으로 통일한다.
     * - {@link ExpiredJwtException}만 AUTH_004로 구분 (UX상 재로그인 안내가 다름)
     * - 그 외 모든 JWT 관련 예외는 AUTH_005로 통합 (사유 노출 최소화)
     *
     * <p>KAN-105: 검증 실패도 REFRESH_REJECTED 감사 로그. actorId는 토큰에서 추출 불가 → null.
     */
    private RefreshClaims verifyOrThrow(String refreshToken) {
        try {
            return tokenIssuer.verifyRefresh(refreshToken);
        } catch (ExpiredJwtException e) {
            auditLogger.emitFailure(SecurityAuditEventType.REFRESH_REJECTED, null,
                    ErrorCode.REFRESH_TOKEN_EXPIRED.getCode(),
                    Map.of("reasonDetail", "jwt_expired"));
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            auditLogger.emitFailure(SecurityAuditEventType.REFRESH_REJECTED, null,
                    ErrorCode.REFRESH_TOKEN_INVALID.getCode(),
                    Map.of("reasonDetail", "jwt_invalid"));
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }
}
