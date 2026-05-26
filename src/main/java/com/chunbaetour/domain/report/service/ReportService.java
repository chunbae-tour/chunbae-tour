package com.chunbaetour.domain.report.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import com.chunbaetour.domain.report.dto.request.MerchantReportResolveRequest;
import com.chunbaetour.domain.report.dto.request.ReportResolveRequest;
import com.chunbaetour.domain.report.dto.response.ReportResolveResponse;
import com.chunbaetour.domain.report.dto.response.ReportResponse;
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.repository.ReportRepository;
import com.chunbaetour.domain.report.type.ReportAction;
import com.chunbaetour.domain.report.type.ReportStatus;
import com.chunbaetour.domain.report.type.ReportTargetType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 서비스 (KAN-91 신고 목록 조회 / KAN-92 신고 처리 / KAN-93 자동 숨김).
 * AdminReportController → ReportService 직접 호출 (spec Section 3-1).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final Pattern CURSOR_PATTERN = Pattern.compile("^\\{\"id\":(\\d+)\\}$");
    private static final int AUTO_HIDE_THRESHOLD = 3;

    private final ReportRepository reportRepository;
    private final AccountRepository accountRepository;
    private final CompanionPostRepository companionPostRepository;
    private final FreePostRepository freePostRepository;
    private final CommentRepository commentRepository;

    // ── KAN-91: 신고 목록 조회 ────────────────────────────────────────────

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
        List<Report> reports;

        if (status == null) {
            reports = (cursor == null)
                    ? reportRepository.findAllOrderByIdDesc(pageable)
                    : reportRepository.findByIdLessThanOrderByIdDesc(decodeCursor(cursor), pageable);
        } else {
            reports = (cursor == null)
                    ? reportRepository.findByStatusOrderByIdDesc(status, pageable)
                    : reportRepository.findByStatusAndIdLessThanOrderByIdDesc(status, decodeCursor(cursor), pageable);
        }

        boolean hasNext = reports.size() > size;
        List<Report> content = hasNext ? reports.subList(0, size) : reports;
        String nextCursor = hasNext ? encodeCursor(content.get(content.size() - 1).getId()) : null;

        List<ReportResponse> responses = content.stream()
                .map(r -> ReportResponse.of(r, resolveNickname(r.getReporterId())))
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    /**
     * 관리자 신고 단건 상세 조회.
     * AdminReportController 전용 — SecurityConfig에서 ADMIN 역할 보장.
     */
    public ReportResponse getReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        return ReportResponse.of(report, resolveNickname(report.getReporterId()));
    }

    /**
     * 사용자 신고 단건 조회 — 본인이 신고한 건만 허용.
     * ReportController(USER 전용) 에서 호출.
     *
     * @param reportId    조회할 신고 ID
     * @param requesterId 요청자 userId (@AuthenticationPrincipal)
     * @throws BusinessException REPORT_NOT_FOUND: 신고 없음
     * @throws BusinessException ACCESS_DENIED: 본인 신고 아님
     */
    public ReportResponse getMyReport(Long reportId, Long requesterId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        if (!report.getReporterId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return ReportResponse.of(report, resolveNickname(report.getReporterId()));
    }

    // ── KAN-92: 신고 처리 ────────────────────────────────────────────────

    /**
     * 콘텐츠 신고 처리 (POST·COMMENT·REVIEW·USER).
     * MERCHANT 신고에 이 엔드포인트를 사용하면 REPORT_WRONG_ENDPOINT 에러.
     *
     * @param reportId      처리할 신고 ID
     * @param adminUsername 처리 관리자 (@AuthenticationPrincipal Account의 username/email)
     * @param request       처리 요청 (action, adminNote)
     * @throws BusinessException REPORT_NOT_FOUND: 신고 없음
     * @throws BusinessException REPORT_ALREADY_RESOLVED: 이미 처리된 신고
     * @throws BusinessException REPORT_WRONG_ENDPOINT: MERCHANT 신고에 이 엔드포인트 사용
     */
    @Transactional
    public ReportResolveResponse resolveReport(Long reportId, Long adminId,
                                               ReportResolveRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        if (!report.isPending()) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_RESOLVED);
        }
        if (report.getTargetType() == ReportTargetType.MERCHANT) {
            throw new BusinessException(ErrorCode.REPORT_WRONG_ENDPOINT);
        }

        ReportAction action = request.action();
        // MERCHANT 전용 액션은 콘텐츠 신고에서 사용 불가
        if (action == ReportAction.HIDE_SHOP || action == ReportAction.REVOKE_MERCHANT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        applyContentAction(action, report.getTargetType(), report.getTargetId());

        String adminNickname = resolveNickname(adminId);
        if (action == ReportAction.DISMISS) {
            report.dismiss(request.adminNote(), adminNickname);
        } else {
            report.resolve(action, request.adminNote(), adminNickname);
        }

        return ReportResolveResponse.of(report);
    }

    /**
     * 가게 신고 처리 (MERCHANT 전용).
     * 콘텐츠 신고에 이 엔드포인트를 사용하면 REPORT_WRONG_ENDPOINT 에러.
     *
     * @param reportId      처리할 신고 ID
     * @param adminUsername 처리 관리자
     * @param request       처리 요청 (HIDE_SHOP·REVOKE_MERCHANT·DISMISS, adminNote)
     * @throws BusinessException REPORT_NOT_FOUND: 신고 없음
     * @throws BusinessException REPORT_ALREADY_RESOLVED: 이미 처리된 신고
     * @throws BusinessException REPORT_WRONG_ENDPOINT: 콘텐츠 신고에 이 엔드포인트 사용
     */
    @Transactional
    public ReportResolveResponse resolveMerchantReport(Long reportId, Long adminId,
                                                       MerchantReportResolveRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        if (!report.isPending()) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_RESOLVED);
        }
        if (report.getTargetType() != ReportTargetType.MERCHANT) {
            throw new BusinessException(ErrorCode.REPORT_WRONG_ENDPOINT);
        }

        ReportAction action = request.action();
        // 콘텐츠 전용 액션은 가게 신고에서 사용 불가
        if (action == ReportAction.WARNING || action == ReportAction.SUSPEND
                || action == ReportAction.DELETE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        applyMerchantAction(action, report.getTargetId());

        String adminNickname = resolveNickname(adminId);
        if (action == ReportAction.DISMISS) {
            report.dismiss(request.adminNote(), adminNickname);
        } else {
            report.resolve(action, request.adminNote(), adminNickname);
        }

        return ReportResolveResponse.of(report);
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────

    /**
     * 콘텐츠 신고 액션 적용.
     * DELETE: 대상 콘텐츠 삭제. SUSPEND: 대상 또는 콘텐츠 작성자 계정 정지. WARNING/DISMISS: 상태만 기록.
     */
    private void applyContentAction(ReportAction action, ReportTargetType targetType, Long targetId) {
        switch (action) {
            case DELETE -> deleteTargetContent(targetType, targetId);
            case SUSPEND -> suspendTargetAuthor(targetType, targetId);
            case WARNING, DISMISS -> { /* MVP: 상태 기록만 */ }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /**
     * 가게 신고 액션 적용.
     * HIDE_SHOP: 가게 비공개(TODO: Shop 도메인 구현 후 연결). REVOKE_MERCHANT: 상인 인증 취소.
     */
    private void applyMerchantAction(ReportAction action, Long targetId) {
        switch (action) {
            case HIDE_SHOP -> {
                // TODO: Shop 도메인 구현 후 ShopRepository.findById(targetId).hide() 연결 (신현민 파트)
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

    /**
     * 신고 대상 콘텐츠 삭제.
     * POST: companion 우선 조회 → 없으면 free 조회. REVIEW: TODO.
     */
    private void deleteTargetContent(ReportTargetType targetType, Long targetId) {
        switch (targetType) {
            case POST -> {
                companionPostRepository.findById(targetId).ifPresentOrElse(
                        CompanionPost::delete,
                        () -> freePostRepository.findById(targetId).ifPresent(FreePost::delete)
                );
            }
            case COMMENT -> commentRepository.findById(targetId).ifPresent(Comment::delete);
            case USER -> {
                // 사용자 신고 DELETE: 계정 정지 (계정 완전 삭제는 별도 절차 필요)
                accountRepository.findById(targetId).ifPresent(Account::suspend);
            }
            case REVIEW -> { /* TODO: 리뷰 도메인 구현 후 연결 */ }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /**
     * 콘텐츠 작성자 계정 정지.
     * POST·COMMENT: 작성자 authorId 조회 후 정지. USER: targetId가 직접 대상 계정.
     */
    private void suspendTargetAuthor(ReportTargetType targetType, Long targetId) {
        Long authorId = switch (targetType) {
            case POST -> companionPostRepository.findById(targetId)
                    .map(CompanionPost::getAuthorId)
                    .orElseGet(() -> freePostRepository.findById(targetId)
                            .map(FreePost::getAuthorId).orElse(null));
            case COMMENT -> commentRepository.findById(targetId)
                    .map(Comment::getAuthorId).orElse(null);
            case USER -> targetId;
            case REVIEW -> null; // TODO: 리뷰 도메인 구현 후 연결
            default -> null;
        };

        if (authorId != null) {
            accountRepository.findById(authorId).ifPresent(Account::suspend);
        }
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
            return ReportStatus.valueOf(statusParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String encodeCursor(Long id) {
        String json = "{\"id\":" + id + "}";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private Long decodeCursor(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String json = new String(decoded, StandardCharsets.UTF_8);
            Matcher matcher = CURSOR_PATTERN.matcher(json);
            if (!matcher.matches()) throw new IllegalArgumentException("invalid cursor format");
            return Long.parseLong(matcher.group(1));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
