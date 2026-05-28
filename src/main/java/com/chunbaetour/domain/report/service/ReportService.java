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
import com.chunbaetour.domain.report.dto.request.MerchantReportResolveRequest;
import com.chunbaetour.domain.report.dto.request.ReportResolveRequest;
import com.chunbaetour.domain.report.dto.response.ReportDetailResponse;
import com.chunbaetour.domain.report.dto.response.ReportResolveResponse;
import com.chunbaetour.domain.report.dto.response.ReportResponse;
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.ReportRepository;
import com.chunbaetour.domain.report.type.ReportAction;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 서비스.
 * KAN-90: 신고 접수 / 내 신고 조회
 * KAN-91: 관리자 신고 목록·상세 조회
 * KAN-92: 관리자 신고 처리 (콘텐츠·가게 분리)
 * KAN-93: 누적 신고 자동 숨김
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    /** n건 이상 신고 시 콘텐츠 자동 숨김 임계값 (KAN-93) */
    private static final int AUTO_HIDE_THRESHOLD = 3;

    private final ReportRepository reportRepository;
    private final AccountRepository accountRepository;
    private final CompanionPostRepository companionPostRepository;
    private final FreePostRepository freePostRepository;
    private final CommentRepository commentRepository;

    // ── KAN-90: 신고 접수 ─────────────────────────────────────────────────

    @Transactional
    public ReportCreateResponse create(Long reporterId, ReportCreateRequest request) {
        // USER·MERCHANT 자기신고 — DB 없이 즉시 차단
        if ((request.targetType() == ReportTargetType.USER
                || request.targetType() == ReportTargetType.MERCHANT)
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
            ReportCreateResponse response = ReportCreateResponse.of(reportRepository.saveAndFlush(report));

            // KAN-93: 누적 신고 수가 임계값 도달 시 콘텐츠 자동 숨김
            autoHideIfThresholdReached(request.targetType(), request.targetId());

            return response;
        } catch (DataIntegrityViolationException e) {
            String msg = e.getMostSpecificCause().getMessage();
            if (msg != null && msg.contains("uk_reports_reporter_target")) {
                throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
            }
            throw e;
        }
    }

    // ── KAN-90: 내 신고 조회 ──────────────────────────────────────────────

    public CursorPageResponse<MyReportResponse> getMyReports(Long reporterId, String cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<Report> reports = (cursorId == null)
                ? reportRepository.findByReporterIdOrderByIdDesc(reporterId, pageable)
                : reportRepository.findByReporterIdAndIdLessThanOrderByIdDesc(reporterId, cursorId, pageable);

        boolean hasNext = reports.size() > size;
        List<Report> content = hasNext ? reports.subList(0, size) : reports;
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        return new CursorPageResponse<>(
                content.stream().map(MyReportResponse::of).toList(),
                nextCursor, hasNext, content.size());
    }

    /**
     * 내 신고 단건 조회 — 신고 없음·타인 신고 모두 REPORT_NOT_FOUND (reportId enumeration 차단).
     */
    public MyReportResponse getMyReport(Long reportId, Long requesterId) {
        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getReporterId().equals(requesterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        return MyReportResponse.of(report);
    }

    // ── KAN-91: 관리자 신고 조회 ──────────────────────────────────────────

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

        // N+1 방지: 신고자 ID 일괄 조회 후 Map 매핑
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
     */
    public ReportDetailResponse getReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        TargetDetail detail = resolveTargetDetail(report.getTargetType(), report.getTargetId());
        return ReportDetailResponse.of(report, resolveNickname(report.getReporterId()),
                detail.title(), detail.content(), detail.imageUrls());
    }

    // ── KAN-92: 관리자 신고 처리 ──────────────────────────────────────────

    /**
     * 콘텐츠 신고 처리 (POST·COMMENT·USER).
     * MERCHANT 신고에 이 엔드포인트를 사용하면 REPORT_WRONG_ENDPOINT 에러.
     */
    @Transactional
    public ReportResolveResponse resolveReport(Long reportId, Long adminId,
                                               ReportResolveRequest request) {
        try {
            Report report = reportRepository.findById(reportId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

            if (!report.isPending()) {
                throw new BusinessException(ErrorCode.REPORT_ALREADY_RESOLVED);
            }
            if (report.getTargetType() == ReportTargetType.MERCHANT) {
                throw new BusinessException(ErrorCode.REPORT_WRONG_ENDPOINT);
            }
            // TODO(KAN-152): REVIEW enum 추가 후 명시적 가드 추가
            // if (report.getTargetType() == ReportTargetType.REVIEW) {
            //     throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND);
            // }

            ReportAction action = request.action();
            if (action == ReportAction.HIDE_SHOP || action == ReportAction.REVOKE_MERCHANT) {
                throw new BusinessException(ErrorCode.REPORT_WRONG_ENDPOINT);
            }

            applyContentAction(action, report.getTargetType(), report.getTargetId());

            String adminNickname = resolveNickname(adminId);
            if (action == ReportAction.DISMISS) {
                report.dismiss(request.adminNote(), adminNickname);
            } else {
                report.resolve(action, request.adminNote(), adminNickname);
            }
            reportRepository.saveAndFlush(report);

            return ReportResolveResponse.of(report);
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_RESOLVED);
        }
    }

    /**
     * 가게 신고 처리 (MERCHANT 전용).
     * 콘텐츠 신고에 이 엔드포인트를 사용하면 REPORT_WRONG_ENDPOINT 에러.
     */
    @Transactional
    public ReportResolveResponse resolveMerchantReport(Long reportId, Long adminId,
                                                       MerchantReportResolveRequest request) {
        try {
            Report report = reportRepository.findById(reportId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

            if (!report.isPending()) {
                throw new BusinessException(ErrorCode.REPORT_ALREADY_RESOLVED);
            }
            if (report.getTargetType() != ReportTargetType.MERCHANT) {
                throw new BusinessException(ErrorCode.REPORT_WRONG_ENDPOINT);
            }

            ReportAction action = request.action();
            if (action == ReportAction.WARNING || action == ReportAction.SUSPEND
                    || action == ReportAction.DELETE) {
                throw new BusinessException(ErrorCode.REPORT_WRONG_ENDPOINT);
            }

            applyMerchantAction(action, report.getTargetId());

            String adminNickname = resolveNickname(adminId);
            if (action == ReportAction.DISMISS) {
                report.dismiss(request.adminNote(), adminNickname);
            } else {
                report.resolve(action, request.adminNote(), adminNickname);
            }
            reportRepository.saveAndFlush(report);

            return ReportResolveResponse.of(report);
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_RESOLVED);
        }
    }

    // ── KAN-93: 자동 숨김 ─────────────────────────────────────────────────

    /**
     * 누적 신고 수가 AUTO_HIDE_THRESHOLD 이상이면 콘텐츠 자동 숨김.
     * USER·MERCHANT는 자동 조치 생략 — 계정 정지·가게 비공개는 관리자가 직접 판단.
     * 이미 삭제된 콘텐츠는 filter 조건으로 중복 처리 방지.
     */
    private void autoHideIfThresholdReached(ReportTargetType targetType, Long targetId) {
        long count = reportRepository.countByTargetTypeAndTargetId(targetType, targetId);
        if (count < AUTO_HIDE_THRESHOLD) return;

        switch (targetType) {
            case POST_COMPANION -> companionPostRepository.findById(targetId)
                    .filter(p -> p.getStatus() == CompanionPostStatus.ACTIVE)
                    .ifPresent(CompanionPost::hide);
            case POST_FREE -> freePostRepository.findById(targetId)
                    .filter(p -> p.getStatus() == FreePostStatus.ACTIVE)
                    .ifPresent(FreePost::hide);
            case COMMENT -> commentRepository.findById(targetId)
                    .filter(c -> c.getStatus() != CommentStatus.DELETED)
                    .ifPresent(Comment::delete);
            case USER, MERCHANT -> {
                // 자동 조치 생략 — 관리자 수동 처리 필요
            }
            // TODO(KAN-152): REVIEW enum 추가 후 case REVIEW -> { /* 자동 숨김 처리 */ } 추가
        }
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────

    private void applyContentAction(ReportAction action, ReportTargetType targetType, Long targetId) {
        switch (action) {
            case DELETE -> deleteTargetContent(targetType, targetId);
            case SUSPEND -> suspendTargetAuthor(targetType, targetId);
            case WARNING, DISMISS -> { /* MVP: 상태 기록만 */ }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void applyMerchantAction(ReportAction action, Long targetId) {
        switch (action) {
            case HIDE_SHOP -> {
                // TODO: Merchant 도메인 연동 후 구현 (신현민 파트).
                // 현재 신고 상태는 RESOLVED/HIDE_SHOP으로 기록되나 실제 가게 숨김은 미처리.
                log.warn("HIDE_SHOP 미구현 — 가게 숨김 처리 보류, targetId={}", targetId);
            }
            case REVOKE_MERCHANT -> {
                Account merchant = accountRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                merchant.revokeToUser();
            }
            case DISMISS -> { /* 무시, 상태 기록만 */ }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void deleteTargetContent(ReportTargetType targetType, Long targetId) {
        switch (targetType) {
            case POST_COMPANION -> {
                if (companionPostRepository.findById(targetId).map(post -> { post.hide(); return true; }).isEmpty()) {
                    log.warn("deleteTargetContent: POST_COMPANION not found, targetId={}", targetId);
                }
            }
            case POST_FREE -> {
                if (freePostRepository.findById(targetId).map(post -> { post.hide(); return true; }).isEmpty()) {
                    log.warn("deleteTargetContent: POST_FREE not found, targetId={}", targetId);
                }
            }
            case COMMENT -> {
                if (commentRepository.findById(targetId).map(comment -> { comment.delete(); return true; }).isEmpty()) {
                    log.warn("deleteTargetContent: COMMENT not found, targetId={}", targetId);
                }
            }
            case USER -> {
                if (accountRepository.findById(targetId).map(acc -> { acc.suspend(); return true; }).isEmpty()) {
                    log.warn("deleteTargetContent: USER not found, targetId={}", targetId);
                }
            }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void suspendTargetAuthor(ReportTargetType targetType, Long targetId) {
        Long authorId = switch (targetType) {
            case POST_COMPANION -> companionPostRepository.findById(targetId)
                    .map(CompanionPost::getAuthorId).orElse(null);
            case POST_FREE -> freePostRepository.findById(targetId)
                    .map(FreePost::getAuthorId).orElse(null);
            case COMMENT -> commentRepository.findById(targetId)
                    .map(Comment::getAuthorId).orElse(null);
            case USER -> targetId;
            default -> null;
        };

        if (authorId == null) {
            log.warn("suspendTargetAuthor: authorId 조회 실패, targetType={}, targetId={}", targetType, targetId);
            return;
        }
        if (accountRepository.findById(authorId).map(acc -> { acc.suspend(); return true; }).isEmpty()) {
            log.warn("suspendTargetAuthor: 계정 없음, authorId={}", authorId);
        }
    }

    /**
     * 신고 대상 상세 정보 조회 — title·content·imageUrls.
     * POST_FREE: title·content·imageUrls 모두 반환.
     * POST_COMPANION: title·content 반환, imageUrls=null.
     * COMMENT: content만 반환, title·imageUrls=null.
     * USER·MERCHANT: content(닉네임)만 반환, title·imageUrls=null.
     */
    private TargetDetail resolveTargetDetail(ReportTargetType targetType, Long targetId) {
        return switch (targetType) {
            case POST_FREE -> freePostRepository.findById(targetId)
                    .map(p -> new TargetDetail(p.getTitle(), p.getContent(), p.getImageUrls()))
                    .orElse(TargetDetail.empty());
            case POST_COMPANION -> companionPostRepository.findById(targetId)
                    .map(p -> new TargetDetail(p.getTitle(), p.getContent(), null))
                    .orElse(TargetDetail.empty());
            case COMMENT -> commentRepository.findById(targetId)
                    .map(c -> new TargetDetail(null, c.getContent(), null))
                    .orElse(TargetDetail.empty());
            case USER, MERCHANT -> accountRepository.findById(targetId)
                    .map(a -> new TargetDetail(null, a.getNickname(), null))
                    .orElse(TargetDetail.empty());
            // TODO(KAN-152): REVIEW 도메인 구현 후 case 추가 — title·content·imageUrls 반환
        };
    }

    /** 신고 대상 콘텐츠 정보 내부 전달 객체. */
    private record TargetDetail(String title, String content, java.util.List<String> imageUrls) {
        static TargetDetail empty() {
            return new TargetDetail(null, null, null);
        }
    }

    private String resolveNickname(Long accountId) {
        return accountRepository.findById(accountId)
                .map(Account::getNickname)
                .orElse("탈퇴한 사용자");
    }

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
                Account account = accountRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (account.getRole() != Role.USER) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND);
                }
            }
            case MERCHANT -> {
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
            // REVIEW: 리뷰 도메인 구현(KAN-152) 완료 후 case 추가 필요
            default -> throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND);
        }
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
