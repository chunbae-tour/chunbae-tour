package com.chunbaetour.domain.report.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.comment.entity.CommentStatus;
import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import com.chunbaetour.domain.report.dto.MyReportResponse;
import com.chunbaetour.domain.report.dto.ReportCreateRequest;
import com.chunbaetour.domain.report.dto.ReportCreateResponse;
import com.chunbaetour.domain.report.dto.response.ReportDetailResponse;
import com.chunbaetour.domain.report.dto.response.ReportResponse;
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.ReportRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 서비스 (KAN-90 신고 접수·내 신고 조회 / KAN-91 관리자 신고 목록·상세).
 * AdminReportController → ReportService 직접 호출 (spec Section 3-1).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final AccountRepository accountRepository;
    private final CompanionPostRepository companionPostRepository;
    private final FreePostRepository freePostRepository;
    private final CommentRepository commentRepository;

    // ── KAN-90: 신고 접수 ─────────────────────────────────────────────────

    @Transactional
    public ReportCreateResponse create(Long reporterId, ReportCreateRequest request) {
        // USER 자기신고 — DB 없이 즉시 차단 (ID 일치 비교)
        // MERCHANT는 role 검증 후 판정 (자기 ID라도 Role.USER면 REPORT_TARGET_NOT_FOUND가 맞음)
        // 게시글·댓글 자기신고 — validateTargetExists 내부에서 authorId 비교 후 차단
        if (request.targetType() == ReportTargetType.USER
                && request.targetId().equals(reporterId)) {
            throw new BusinessException(ErrorCode.REPORT_SELF);
        }

        // 검증 순서: DB 조회 없는 자기신고 체크 → 존재 확인(DB 1회, 게시글/댓글 자기신고 포함) → 중복 확인(DB 1회)
        validateReportTarget(request.targetType(), request.targetId(), reporterId);

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporterId, request.targetType(), request.targetId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
        }

        try {
            Report report = Report.create(
                    reporterId, request.targetType(), request.targetId(),
                    request.reason(), request.description());
            // saveAndFlush: 트랜잭션 내 즉시 flush → DB 유니크 제약 위반 시 여기서 예외 발생
            return ReportCreateResponse.of(reportRepository.saveAndFlush(report));
        } catch (DataIntegrityViolationException e) {
            // uk_reports_reporter_target 제약 위반 = 동시 중복 신고, 그 외는 재throw
            String msg = e.getMostSpecificCause().getMessage();
            if (msg != null && msg.contains("uk_reports_reporter_target")) {
                throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
            }
            throw e;
        }
    }

    // ── KAN-90: 내 신고 내역 조회 ──────────────────────────────────────────

    /**
     * 내가 신고한 내역 cursor 페이징 조회.
     *
     * @param reporterId 요청자 userId (@AuthenticationPrincipal)
     * @param cursor     Base64 인코딩된 cursor (null = 첫 페이지)
     * @param size       페이지 크기
     */
    public CursorPageResponse<MyReportResponse> getMyReports(Long reporterId, String cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<Report> reports = (cursorId == null)
                ? reportRepository.findByReporterIdOrderByIdDesc(reporterId, pageable)
                : reportRepository.findByReporterIdAndIdLessThanOrderByIdDesc(
                        reporterId, cursorId, pageable);

        boolean hasNext = reports.size() > size;
        List<Report> content = hasNext ? reports.subList(0, size) : reports;
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        return new CursorPageResponse<>(
                content.stream().map(MyReportResponse::of).toList(),
                nextCursor, hasNext, content.size());
    }

    /**
     * 내 신고 단건 조회 — 본인이 신고한 건만 허용.
     * 신고 없음·타인 신고 모두 RESOURCE_NOT_FOUND 통일 → reportId enumeration 차단.
     *
     * @throws BusinessException RESOURCE_NOT_FOUND: 신고 없음 또는 본인 신고 아님
     */
    public MyReportResponse getMyReport(Long reportId, Long requesterId) {
        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getReporterId().equals(requesterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return MyReportResponse.of(report);
    }

    // ── KAN-91: 관리자 신고 목록 조회 ────────────────────────────────────

    /**
     * 관리자 신고 목록 cursor 페이징 조회.
     *
     * @param statusParam null = 전체, 그 외 = 해당 상태만
     * @param cursor      Base64 인코딩된 cursor (null = 첫 페이지)
     * @param size        페이지 크기
     */
    public CursorPageResponse<ReportResponse> getReports(String statusParam, String cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);

        ReportStatus status = parseStatus(statusParam);
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<Report> reports;

        if (status == null) {
            reports = (cursorId == null)
                    ? reportRepository.findAllOrderByIdDesc(pageable)
                    : reportRepository.findByIdLessThanOrderByIdDesc(cursorId, pageable);
        } else {
            reports = (cursorId == null)
                    ? reportRepository.findByStatusOrderByIdDesc(status, pageable)
                    : reportRepository.findByStatusAndIdLessThanOrderByIdDesc(status, cursorId, pageable);
        }

        boolean hasNext = reports.size() > size;
        List<Report> content = hasNext ? reports.subList(0, size) : reports;
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        // N+1 방지: 신고자 ID를 일괄 조회 후 Map으로 매핑
        Set<Long> reporterIds = content.stream().map(Report::getReporterId).collect(Collectors.toSet());
        Map<Long, String> nicknameMap = accountRepository.findAllById(reporterIds).stream()
                .collect(Collectors.toMap(Account::getId, Account::getNickname));

        List<ReportResponse> responses = content.stream()
                .map(r -> ReportResponse.of(r, nicknameMap.getOrDefault(r.getReporterId(), "탈퇴한 사용자")))
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    /**
     * 관리자 신고 단건 상세 조회.
     * AdminReportController 전용 — SecurityConfig에서 ADMIN 역할 보장.
     */
    public ReportDetailResponse getReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        String targetContent = resolveTargetContent(report.getTargetType(), report.getTargetId());
        return ReportDetailResponse.of(report, resolveNickname(report.getReporterId()), targetContent);
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────

    // 신고 대상 존재·활성 상태 검증 + 자기신고 차단 (게시글/댓글/MERCHANT)
    private void validateReportTarget(ReportTargetType targetType, Long targetId, Long reporterId) {
        switch (targetType) {
            case POST_COMPANION -> {
                CompanionPost post = companionPostRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (post.getStatus() != CompanionPostStatus.ACTIVE) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
                }
                if (post.getAuthorId().equals(reporterId)) {
                    throw new BusinessException(ErrorCode.REPORT_SELF);
                }
            }
            case POST_FREE -> {
                FreePost post = freePostRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (post.getStatus() != FreePostStatus.ACTIVE) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
                }
                if (post.getAuthorId().equals(reporterId)) {
                    throw new BusinessException(ErrorCode.REPORT_SELF);
                }
            }
            case USER -> {
                // Account has @SQLRestriction("deleted_at IS NULL") — deleted accounts return empty
                Account account = accountRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (account.getRole() != Role.USER) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND);
                }
            }
            case MERCHANT -> {
                // MERCHANT는 별도 테이블 없이 Account.role로 구분 — role 불일치 시 대상 없음으로 처리
                Account merchant = accountRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (merchant.getRole() != Role.MERCHANT) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND);
                }
                if (merchant.getId().equals(reporterId)) {
                    throw new BusinessException(ErrorCode.REPORT_SELF);
                }
            }
            case COMMENT -> {
                Comment comment = commentRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (comment.getStatus() == CommentStatus.DELETED) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
                }
                if (comment.getAuthorId().equals(reporterId)) {
                    throw new BusinessException(ErrorCode.REPORT_SELF);
                }
            }
        }
    }

    /**
     * targetType + targetId → 신고 대상 콘텐츠 텍스트.
     */
    private String resolveTargetContent(ReportTargetType type, Long targetId) {
        return switch (type) {
            case POST_COMPANION -> companionPostRepository.findById(targetId)
                    .map(CompanionPost::getContent).orElse(null);
            case POST_FREE -> freePostRepository.findById(targetId)
                    .map(FreePost::getContent).orElse(null);
            case COMMENT -> commentRepository.findById(targetId)
                    .map(Comment::getContent).orElse(null);
            case USER, MERCHANT -> accountRepository.findById(targetId)
                    .map(Account::getNickname).orElse(null);
        };
    }

    /**
     * reporterId → 닉네임. 탈퇴 계정이면 "탈퇴한 사용자" 반환.
     */
    private String resolveNickname(Long reporterId) {
        return accountRepository.findById(reporterId)
                .map(Account::getNickname)
                .orElse("탈퇴한 사용자");
    }

    private ReportStatus parseStatus(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) return null;
        try {
            return ReportStatus.valueOf(statusParam.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
