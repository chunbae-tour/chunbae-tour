package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.PatchUserMeRequest;
import com.chunbaetour.domain.auth.dto.UserMeResponse;
import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.LogoutTokenStore;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 마이페이지 본인 정보 조회 서비스 (Epic A S1).
 *
 * <p>KAN-23 S2~S5의 인증 흐름 검증용 임시 ping endpoint는 본 서비스의 {@code GET /api/v1/users/me}로 대체.
 * 임시 ping은 KAN-129 (Epic A S4)에서 일괄 제거됨.
 *
 * <p>호출 흐름:
 * <ol>
 *   <li>JwtAuthenticationFilter가 Access Token 검증 + SecurityContext에 userId 설정</li>
 *   <li>UserMeController가 {@code @AuthenticationPrincipal Long userId}로 받음</li>
 *   <li>본 서비스의 {@link #getMe}가 userId로 Account 조회 후 {@link UserMeResponse}로 변환</li>
 * </ol>
 *
 * <p>{@code SecurityContext}에 들어간 userId는 JWT 검증을 거쳤지만, 토큰 발급 후 사용자가 탈퇴(soft-delete)했을
 * 가능성이 있다. {@code @SQLRestriction("deleted_at IS NULL")}로 자동 필터링되어 {@code findById}가 빈 결과를
 * 반환하므로 그 시점에 {@link ErrorCode#AUTHENTICATION_REQUIRED}(AUTH_006)로 응답한다 — 클라이언트는 재로그인
 * 안내. 탈퇴 사실을 명시 노출하지 않는 보안 원칙.
 */
@Service
@RequiredArgsConstructor
public class UserMeService {

    private final AccountRepository accountRepository;
    private final LogoutTokenStore logoutTokenStore;
    private final Clock clock;

    /**
     * 본인 정보 조회.
     *
     * <p>{@code @Transactional(readOnly = true)} — 조회 전용이므로 readOnly로 두어 1차 캐시 비활성화와
     * 옵티마이저 힌트를 받는다.
     *
     * @param userId SecurityContext에서 추출한 본인 ID
     * @return 안전한 응답 DTO
     * @throws BusinessException AUTH_006 — 탈퇴 또는 강제 삭제된 사용자가 토큰만 들고 호출한 경우
     */
    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        return UserMeResponse.from(account);
    }

    /**
     * 마이페이지 PATCH /users/me — partial update (Epic A S2, KAN-127).
     *
     * <p>흐름:
     * <ol>
     *   <li>request가 empty(모든 필드 null)면 현재 상태 그대로 응답 (DB write 0회, read 1회 — 응답 데이터 필요로
     *       getMe()의 findById는 발생. 닉네임 중복 체크 + dirty update + flush는 모두 회피)</li>
     *   <li>userId로 Account 조회 (탈퇴자는 SQLRestriction으로 자동 제외 → AUTH_006)</li>
     *   <li>nickname이 있고 본인 외 중복이면 AUTH_009 거부</li>
     *   <li>{@link Account#updateProfile}로 변경 — null 인자는 도메인 메서드가 무시</li>
     *   <li>JPA dirty checking으로 자동 flush. 변경된 사용자 정보를 응답 DTO로 반환</li>
     * </ol>
     *
     * <p><b>보안 회귀 가드</b>: userId는 SecurityContext에서 추출되어 호출자가 임의 변조 불가.
     * PathVariable 미사용 → 다른 사용자 정보 변경 시도 원천 차단.
     *
     * <p>닉네임 중복 race 처리: existsByNicknameAndIdNot 체크 통과 후 saveAndFlush 사이에 동시 가입이 있으면
     * DB unique constraint가 차단해 {@link DataIntegrityViolationException}이 발생. 본 서비스가 직접 catch해
     * {@link ErrorCode#DUPLICATE_NICKNAME} (AUTH_009)로 변환한다. GlobalExceptionHandler에 전용 매핑을 두지
     * 않는 이유는 다른 테이블의 unique 위반(예: email)이 같은 응답 코드로 잘못 변환되는 것을 막기 위함.
     *
     * @param userId  SecurityContext userId (본인 외 변조 불가)
     * @param request partial update 요청 (모든 필드 nullable)
     * @return 갱신 후 사용자 정보 (KAN-63 응답 포맷 재사용)
     */
    @Transactional
    public UserMeResponse updateMe(Long userId, PatchUserMeRequest request) {
        if (request.isEmpty()) {
            // 모든 필드 null → noop. 현재 상태 그대로 응답 (idempotent + 빈 PATCH 응답 200 표준).
            return getMe(userId);
        }
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));

        if (request.nickname() != null
                && accountRepository.existsByNicknameAndIdNot(request.nickname(), userId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        account.updateProfile(request.nickname(), request.language(), request.profileImageUrl());

        try {
            // saveAndFlush로 트랜잭션 커밋 전 flush 강제 — race 시 DB unique constraint를 catch 가능 시점에서 잡음.
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // existsByNicknameAndIdNot 통과 → flush 사이에 동시 가입으로 동일 닉네임 점유된 race.
            // GlobalExceptionHandler에 전용 매핑이 없어 fallback 500으로 떨어지는 것을 차단 (CR + LH #1).
            // 다른 unique 위반(예: email)은 catch하지 않아 fallback 알람으로 의도치 않은 케이스 발견 가능.
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        return UserMeResponse.from(account);
    }

    /**
     * 회원 탈퇴 (Epic C S1, KAN-143).
     *
     * <p>흐름:
     * <ol>
     *   <li>userId로 Account 조회. 이미 탈퇴된 계정은 {@code @SQLRestriction}으로 빈 결과 → AUTH_006.</li>
     *   <li>{@link Account#softDelete}로 도메인 상태 전이 — {@code deletedAt} + {@code status=DELETED} 동시 세팅.</li>
     *   <li>트랜잭션 commit 후({@link TransactionSynchronization#afterCommit}) 토큰 cascade 무효화 등록.
     *       <ul>
     *         <li>Refresh Redis 키 삭제: {@code auth:refresh:{userId}}</li>
     *         <li>Access blacklist 등록: {@code auth:blacklist:{accessJti}} (TTL = access 잔여 만료까지)</li>
     *       </ul>
     *       두 작업은 {@link LogoutTokenStore#invalidate}의 Lua script가 원자적으로 처리 (로그아웃 흐름과 공유).
     *   </li>
     * </ol>
     *
     * <p><b>afterCommit 사용 이유</b>: DB rollback 시 Redis 무효화가 남으면 사용자가 토큰을 잃은 채 계정이
     * 그대로 활성화돼 있어 재로그인을 강제로 요구받는다(=실제 탈퇴되지 않았는데 로그아웃됨). commit
     * 성공이 확정된 뒤에만 Redis를 건드려 "DB 탈퇴 ↔ 토큰 무효화"의 일관성을 보장한다. 반대 방향
     * (Redis 성공 + DB 실패)도 같은 분기에서 회피.
     *
     * <p><b>LogoutService.logout과 분리한 이유</b>: 로그아웃은 {@code SecurityAuditEventType.LOGOUT} 감사
     * 이벤트를 발행하지만, 탈퇴는 별도 {@code ACCOUNT_DELETED} 이벤트(S2 KAN-144 범위)로 처리한다.
     * 동일한 audit event를 잘못 emit하지 않도록 service 레벨에서 직접 {@link LogoutTokenStore}만 호출.
     * 토큰 무효화 데이터 저장소는 같은 컴포넌트를 공유 → 로직 복붙 아님.
     *
     * <p><b>UserLike/Wallet cascade는 S2 범위</b>: 본 슬라이스는 인증 흐름 차단까지만. 부속 데이터
     * 정책(보존 vs 삭제)은 S2에서 ADR과 함께 처리.
     *
     * @param userId      SecurityContext에서 추출한 본인 ID (PathVariable 미사용 — PK 변조 차단)
     * @param accessClaims 인증 필터가 검증을 마친 현재 Access Token 클레임 — tokenId/expiresAt만 사용.
     *                     호출자(Controller)가 {@code request.getAttribute(REQUEST_ATTR_ACCESS_CLAIMS)}로 획득.
     * @throws BusinessException AUTH_006 — 이미 탈퇴되었거나 존재하지 않는 사용자.
     */
    @Transactional
    public void deleteMe(Long userId, AccessClaims accessClaims) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));

        // 도메인 상태 전이. 이미 DELETED면 IllegalStateException — @SQLRestriction이 차단하므로 정상
        // 흐름에서는 도달 불가지만 도메인 가드는 유지.
        account.softDelete(LocalDateTime.now(clock));

        // afterCommit에서 사용할 값은 final로 캡처. accessClaims는 record라 immutable이라 안전.
        final long uid = userId;
        final String tokenId = accessClaims.tokenId();
        final java.time.Instant expiresAt = accessClaims.expiresAt();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 잔여 TTL = exp - now. 음수면 LogoutTokenStore가 SET(blacklist)을 스킵하고 Refresh DEL만 실행.
                // LogoutService.logout과 같은 계산식 (KAN-23 S4 패턴 재사용).
                Duration remainingTtl = Duration.between(clock.instant(), expiresAt);
                logoutTokenStore.invalidate(uid, tokenId, remainingTtl);
            }
        });
    }
}
