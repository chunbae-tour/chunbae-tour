package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.PatchUserMeRequest;
import com.chunbaetour.domain.auth.dto.UserMeResponse;
import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.LogoutTokenStore;
import com.chunbaetour.domain.common.audit.SecurityAuditEventType;
import com.chunbaetour.domain.common.audit.SecurityAuditLogger;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.repository.UserLikeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class UserMeService {

    private final AccountRepository accountRepository;
    private final LogoutTokenStore logoutTokenStore;
    private final UserLikeRepository userLikeRepository;
    private final SecurityAuditLogger auditLogger;
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
     * 회원 탈퇴 (Epic C S1+S2, KAN-143+KAN-144).
     *
     * <p>S1(KAN-143) — soft delete + 토큰 cascade 무효화.
     * <br>S2(KAN-144) — UserLike cascade 삭제 + ACCOUNT_DELETED audit emit + 동시 탈퇴 race 가드.
     *
     * <p>흐름:
     * <ol>
     *   <li>{@link AccountRepository#markAsDeleted}로 CAS UPDATE 시도. 영향 row 0이면 이미 탈퇴됨 → AUTH_006.</li>
     *   <li>{@link UserLikeRepository#deleteByUserId}로 사용자 찜 데이터 hard delete (ADR §2 B안).
     *       삭제된 row 수를 audit metadata로 기록.</li>
     *   <li>트랜잭션 commit 후({@link TransactionSynchronization#afterCommit}) 사이드이펙트 등록:
     *       <ul>
     *         <li>토큰 cascade 무효화 — {@link LogoutTokenStore#invalidate} (Refresh DEL + Access blacklist SET 원자 처리)</li>
     *         <li>{@link SecurityAuditEventType#ACCOUNT_DELETED} audit emit — 운영 추적성</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <p><b>동시성 정책 결정 (S2 KAN-144 ADR §5)</b>: CAS UPDATE 채택.
     * <ul>
     *   <li>tx1: {@code UPDATE ... WHERE deleted_at IS NULL} 1행 영향 → UserLike cascade → COMMIT → afterCommit</li>
     *   <li>tx2: 동일 UPDATE가 0행 영향 (이미 deleted_at SET) → AUTH_006 즉시 응답</li>
     * </ul>
     * → ACCOUNT_DELETED audit은 실제 탈퇴 1건당 정확히 1번 발행. PESSIMISTIC_WRITE 대안은 Hibernate +
     * {@code @SQLRestriction}이 결합할 때 stale snapshot 실측 이슈로 기각. {@code @Version} optimistic 대안은
     * Account에 version 컬럼 schema migration이 필요해 prod ddl-auto=validate 위험 → 본 슬라이스 회피.
     * CAS UPDATE는 단일 SQL 명령이라 race-window 자체가 없다 (ADR §5 (d) 채택).
     *
     * <p><b>afterCommit 사용 이유</b>: DB rollback 시 Redis/audit 사이드이펙트가 남으면 "DB는 ACTIVE인데 토큰만
     * 무효화 + audit은 탈퇴 발행" 상태가 만들어진다. commit 성공이 확정된 뒤에만 외부 부작용을 발행해
     * "DB 탈퇴 ↔ 토큰 무효화 ↔ audit"의 일관성을 보장.
     *
     * <p><b>LogoutService.logout과 분리한 이유</b>: 로그아웃은 {@link SecurityAuditEventType#LOGOUT} 이벤트를
     * 발행하지만 탈퇴는 {@link SecurityAuditEventType#ACCOUNT_DELETED}로 구분해야 SIEM/운영 추적이 명확.
     * 데이터 저장소({@link LogoutTokenStore})만 공유 — 로직 복붙 아님.
     *
     * @param userId      SecurityContext에서 추출한 본인 ID (PathVariable 미사용 — PK 변조 차단)
     * @param accessClaims 인증 필터가 검증을 마친 현재 Access Token 클레임 — tokenId/expiresAt + role만 사용.
     *                     호출자(Controller)가 {@code request.getAttribute(REQUEST_ATTR_ACCESS_CLAIMS)}로 획득.
     * @throws BusinessException AUTH_006 — 이미 탈퇴되었거나 존재하지 않는 사용자(동시 탈퇴 race의 두 번째 호출 포함).
     */
    @Transactional
    public void deleteMe(Long userId, AccessClaims accessClaims) {
        // 동시 탈퇴 race 가드 — CAS UPDATE (ADR §5).
        // "deleted_at IS NULL일 때만 status=DELETED + deleted_at=now"로 atomic 처리. 동시 두 요청 중 한 쪽만
        // 1행 UPDATE하고 다른 쪽은 0행. 0이면 이미 누군가 탈퇴 처리한 상태 → AUTH_006으로 폴백.
        //
        // 왜 read-then-write (findByIdWithLock + softDelete) 패턴을 안 쓰는가:
        // PESSIMISTIC_WRITE 락을 적용해도 @SQLRestriction("deleted_at IS NULL")이 FOR UPDATE 쿼리와 결합할 때
        // Hibernate 버전/방언에 따라 tx2가 락 해제 후에도 stale snapshot으로 ACTIVE 엔티티를 반환하는 케이스가
        // 실측됨 (testcontainers MySQL 8.4). CAS UPDATE는 단일 SQL 명령이라 race-window 자체가 존재하지 않아
        // ACCOUNT_DELETED audit이 1건당 정확히 1번 발행되는 invariant를 DB가 직접 보장.
        int updated = accountRepository.markAsDeleted(userId, LocalDateTime.now(clock));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        // UserLike cascade — ADR §2 B안(즉시 hard delete). CAS UPDATE가 호출자 1명에게만 1을 반환하므로 본
        // DELETE는 정확히 1회 실행. flushAutomatically/clearAutomatically로 영속성 컨텍스트 일관성 유지.
        int deletedLikes = userLikeRepository.deleteByUserId(userId);

        // afterCommit에서 사용할 값은 final로 캡처. AccessClaims는 record라 immutable.
        final long uid = userId;
        final String tokenId = accessClaims.tokenId();
        final Instant expiresAt = accessClaims.expiresAt();
        final String roleName = accessClaims.role().name();
        final int likesRemoved = deletedLikes;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 토큰 cascade 무효화 — Refresh Redis 키 DEL + Access blacklist SET (Lua script 원자).
                // try-catch 사유 (PR #207 hyeonmin02 🔴 review): afterCommit 예외는 caller(컨트롤러)로 전파되어
                // DB는 탈퇴 완료인데 클라이언트가 500을 받는 데이터/UX 불일치 발생. 흡수 + log.error로 운영 가시성만 확보.
                // 잔여 Access 토큰은 자연 만료(< 1h)까지 짧은 window 동안만 유효. 재시도/outbox는 별도 KAN.
                try {
                    Duration remainingTtl = Duration.between(clock.instant(), expiresAt);
                    logoutTokenStore.invalidate(uid, tokenId, remainingTtl);
                } catch (RuntimeException e) {
                    log.error("회원 탈퇴 후 토큰 무효화 실패 — DB 탈퇴 완료, Redis 미반영. userId={}", uid, e);
                }

                // ACCOUNT_DELETED audit emit — KAN-105 audit-log-catalog.md 표준 채널 (audit.security logger).
                // SecurityAuditLogger.emitSuccess는 내부에서 try-catch로 audit 손실을 흡수하므로 본 호출은
                // 호출자(비즈니스 흐름)에 예외를 전파하지 않는다 — 추가 try-catch 불필요. (KAN-105 contract)
                //
                // metadata key 명명 — `tokenRole` (DB 현재 role 아닌 Access Token claim 기준).
                // 토큰 발급 후 사용자가 USER → MERCHANT 승격된 뒤 탈퇴 시 audit 값은 발급 시점 role이므로
                // SIEM/감사에서 혼동되지 않도록 키 이름으로 출처를 명시. DB 현재 role 캡처는 별도 SELECT 비용 발생.
                // (PR #217 hyeonmin02 review — audit 출처 명확화)
                auditLogger.emitSuccess(
                        SecurityAuditEventType.ACCOUNT_DELETED,
                        uid,
                        Map.of("tokenRole", roleName, "deletedLikes", String.valueOf(likesRemoved)));
            }
        });
    }
}
