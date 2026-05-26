package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.PatchUserMeRequest;
import com.chunbaetour.domain.auth.dto.UserMeResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 본인 정보 조회 서비스 (Epic A S1).
 *
 * <p>S2~S5에서 인증 흐름 검증용으로 추가한 {@code /api/v1/users/me/ping} 임시 endpoint를 본 서비스가 호출하는
 * {@code GET /api/v1/users/me}로 대체할 준비를 한다. (임시 ping은 Epic A S4 정리 슬라이스에서 일괄 제거.)
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
    public UserMeResponse getMe(long userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        return UserMeResponse.from(account);
    }

    /**
     * 마이페이지 PATCH /users/me — partial update (Epic A S2, KAN-127).
     *
     * <p>흐름:
     * <ol>
     *   <li>request가 empty(모든 필드 null)면 현재 상태 그대로 응답 (불필요한 DB write 회피)</li>
     *   <li>userId로 Account 조회 (탈퇴자는 SQLRestriction으로 자동 제외 → AUTH_006)</li>
     *   <li>nickname이 있고 본인 외 중복이면 AUTH_009 거부</li>
     *   <li>{@link Account#updateProfile}로 변경 — null 인자는 도메인 메서드가 무시</li>
     *   <li>JPA dirty checking으로 자동 flush. 변경된 사용자 정보를 응답 DTO로 반환</li>
     * </ol>
     *
     * <p><b>보안 회귀 가드</b>: userId는 SecurityContext에서 추출되어 호출자가 임의 변조 불가.
     * PathVariable 미사용 → 다른 사용자 정보 변경 시도 원천 차단.
     *
     * <p>닉네임 중복 race 처리: existsByNicknameAndIdNot 체크와 dirty flush 사이에 동시 가입이 있으면
     * DB unique constraint가 최종 차단 (DataIntegrityViolationException → GlobalExceptionHandler가 응답 변환).
     * 본 서비스는 일반 case의 사전 검증만 책임.
     *
     * @param userId  SecurityContext userId (본인 외 변조 불가)
     * @param request partial update 요청 (모든 필드 nullable)
     * @return 갱신 후 사용자 정보 (KAN-63 응답 포맷 재사용)
     */
    @Transactional
    public UserMeResponse updateMe(long userId, PatchUserMeRequest request) {
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
        // 트랜잭션 커밋 시점에 dirty checking으로 자동 update — 명시적 save 불필요.
        return UserMeResponse.from(account);
    }
}
