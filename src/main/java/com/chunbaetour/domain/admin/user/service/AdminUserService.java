package com.chunbaetour.domain.admin.user.service;

import com.chunbaetour.domain.admin.user.dto.response.UserAdminDetailResponse;
import com.chunbaetour.domain.admin.user.dto.response.UserAdminListResponse;
import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.ReportRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 운영자 사용자 관리 서비스 (KAN-180, Admin Epic KAN-177 S02).
 *
 * <p>조회는 클래스 기본 {@code @Transactional(readOnly = true)}, 정지/해제만 쓰기 {@code @Transactional}로 override.
 * 정지/해제 endpoint의 audit 기록은 컨트롤러의 {@link com.chunbaetour.domain.admin.audit.LogAdminAction}
 * AOP가 담당하며 본 서비스는 도메인 상태 전이만 수행한다.
 *
 * <p>{@code getTotalUsers}/{@code getNewUsersToday}/{@code getSuspendedUsers}는 S03 대시보드(KAN-181) 의존 —
 * 사용자 카운트 쿼리는 본 서비스에만 두고 대시보드는 조합만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    // 신규 가입 "오늘" 집계는 한국 영업일 기준 (MerchantHomeService.BUSINESS_ZONE와 동일).
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final AccountRepository accountRepository;
    private final ReportRepository reportRepository;
    private final Clock clock;

    /**
     * 사용자 목록 검색 (keyword/status/role 필터 + cursor 페이징).
     *
     * <p>keyword 공백 문자열은 null로 정규화해 전체 조회로 처리(LIKE '%%' 무의미 매칭 방지). size+1 sentinel로
     * 다음 페이지 존재를 추가 쿼리 없이 판단.
     */
    public CursorPageResponse<UserAdminListResponse> getUsers(
            String keyword, AccountStatus status, Role role, String cursor, int size) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Long cursorId = CursorUtils.decodeSafe(cursor);

        PageRequest pageable = PageRequest.of(0, size + 1);
        List<Account> accounts =
                accountRepository.searchForAdmin(normalizedKeyword, status, role, cursorId, pageable);

        boolean hasNext = accounts.size() > size;
        List<Account> content = hasNext ? accounts.subList(0, size) : accounts;
        String nextCursor = hasNext
                ? CursorUtils.encode(content.get(content.size() - 1).getId())
                : null;

        List<UserAdminListResponse> responses = content.stream()
                .map(UserAdminListResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    /** 사용자 단건 상세 — 가입정보 + 정지상태 + 누적 신고 건수. */
    public UserAdminDetailResponse getUser(Long userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        long reportCount = reportRepository.countByTargetTypeAndTargetId(ReportTargetType.USER, userId);
        return UserAdminDetailResponse.from(account, reportCount);
    }

    /**
     * 사용자 정지 생성/갱신.
     *
     * <p>durationDays가 null이면 무기한(suspendedUntil null), 지정 시 현재 시각 + durationDays로 만료시각 산출.
     * 이미 정지된 계정이면 사유/기간을 갱신한다({@link Account#suspend(String, LocalDateTime)}).
     */
    @Transactional
    public void suspend(Long userId, String reason, Integer durationDays) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        LocalDateTime suspendedUntil = durationDays == null
                ? null
                : LocalDateTime.now(clock).plusDays(durationDays);
        account.suspend(reason, suspendedUntil);
    }

    /** 사용자 정지 해제 — status ACTIVE + 정지 사유/만료시각 초기화. */
    @Transactional
    public void unsuspend(Long userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        account.unsuspend();
    }

    // ── S03 대시보드(KAN-181) 의존 카운트 ───────────────────────────────────

    /** 전체 사용자 수 (탈퇴 제외 — {@code @SQLRestriction} 자동 적용). */
    public long getTotalUsers() {
        return accountRepository.count();
    }

    /**
     * 오늘(한국 영업일, Asia/Seoul) 신규 가입 사용자 수.
     *
     * <p>Clock 빈은 {@code systemUTC}지만 {@code created_at}은 JPA auditing이 서버 기본 zone(prod=KST)으로
     * 기록하므로, {@code clock.withZone(BUSINESS_ZONE)}로 KST 날짜를 산출해 정합을 맞춘다.
     */
    public long getNewUsersToday() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        return accountRepository.countByCreatedAtGreaterThanEqual(today.atStartOfDay());
    }

    /** 정지 상태 사용자 수. */
    public long getSuspendedUsers() {
        return accountRepository.countByStatus(AccountStatus.SUSPENDED);
    }
}
