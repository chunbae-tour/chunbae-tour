package com.chunbaetour.domain.report.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.comment.entity.CommentStatus;
import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import org.springframework.dao.DataIntegrityViolationException;
import com.chunbaetour.domain.report.dto.MyReportResponse;
import com.chunbaetour.domain.report.dto.ReportCreateRequest;
import com.chunbaetour.domain.report.dto.ReportCreateResponse;
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.ReportRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final CompanionPostRepository companionPostRepository;
    private final FreePostRepository freePostRepository;
    private final CommentRepository commentRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public ReportCreateResponse create(Long reporterId, ReportCreateRequest request) {
        // 자기신고 차단: USER·MERCHANT만 체크 — 게시글/댓글은 ID 체계가 달라 비교 무의미
        if ((request.targetType() == ReportTargetType.USER
                || request.targetType() == ReportTargetType.MERCHANT)
                && request.targetId().equals(reporterId)) {
            throw new BusinessException(ErrorCode.REPORT_SELF);
        }

        // 검증 순서: DB 조회 없는 자기신고 체크 → 존재 확인(DB 1회) → 중복 확인(DB 1회) — 빠른 실패 원칙
        validateTargetExists(request.targetType(), request.targetId());

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporterId, request.targetType(), request.targetId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
        }

        try {
            Report report = Report.create(
                    reporterId, request.targetType(), request.targetId(),
                    request.reason(), request.description());
            // saveAndFlush: 트랜잭션 내 즉시 flush → DB 유니크 제약 위반 시 여기서 예외 발생
            // save()만 사용하면 flush가 커밋 시점으로 미뤄져 catch 블록 밖에서 터짐
            return ReportCreateResponse.of(reportRepository.saveAndFlush(report));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 exists 검사를 통과한 중복 신고 → DB 유니크 제약이 원자적으로 차단
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
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

    // 신고 대상 존재·활성 상태 검증 — 타입별로 다른 테이블 조회
    private void validateTargetExists(ReportTargetType targetType, Long targetId) {
        switch (targetType) {
            case POST_COMPANION -> {
                CompanionPost post = companionPostRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (post.getStatus() != CompanionPostStatus.ACTIVE) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
                }
            }
            case POST_FREE -> {
                FreePost post = freePostRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (post.getStatus() != FreePostStatus.ACTIVE) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
                }
            }
            case USER -> {
                // Account has @SQLRestriction("deleted_at IS NULL") — deleted accounts return empty
                accountRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
            }
            case MERCHANT -> {
                // MERCHANT는 별도 테이블 없이 Account.role로 구분 — role 불일치 시 대상 없음으로 처리
                Account merchant = accountRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (merchant.getRole() != Role.MERCHANT) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND);
                }
            }
            case COMMENT -> {
                Comment comment = commentRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (comment.getStatus() == CommentStatus.DELETED) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
                }
            }
        }
    }
}
