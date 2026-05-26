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
import org.springframework.dao.DataIntegrityViolationException;
import com.chunbaetour.domain.report.dto.MyReportResponse;
import com.chunbaetour.domain.report.dto.ReportCreateRequest;
import com.chunbaetour.domain.report.dto.ReportCreateResponse;
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.ReportRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final Pattern CURSOR_PATTERN = Pattern.compile("^\\{\"id\":(\\d+)\\}$");
    /** n건 이상 신고 시 콘텐츠 자동 숨김 임계값 (KAN-93) */
    private static final int AUTO_HIDE_THRESHOLD = 3;

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
            ReportCreateResponse response = ReportCreateResponse.of(reportRepository.saveAndFlush(report));

            // KAN-93: 누적 신고 수가 임계값 도달 시 콘텐츠 자동 숨김
            autoHideIfThresholdReached(request.targetType(), request.targetId());

            return response;
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
        List<Report> reports = (cursor == null)
                ? reportRepository.findByReporterIdOrderByIdDesc(reporterId, pageable)
                : reportRepository.findByReporterIdAndIdLessThanOrderByIdDesc(
                        reporterId, decodeCursor(cursor), pageable);

        boolean hasNext = reports.size() > size;
        List<Report> content = hasNext ? reports.subList(0, size) : reports;
        String nextCursor = hasNext ? encodeCursor(content.get(content.size() - 1).getId()) : null;

        return new CursorPageResponse<>(
                content.stream().map(MyReportResponse::of).toList(),
                nextCursor, hasNext, content.size());
    }

    /**
     * 내 신고 단건 조회 — 본인이 신고한 건만 허용.
     *
     * @throws BusinessException REPORT_TARGET_NOT_FOUND: 신고 없음
     * @throws BusinessException ACCESS_DENIED: 본인 신고 아님
     */
    public MyReportResponse getMyReport(Long reportId, Long requesterId) {
        // REPORT_TARGET_NOT_FOUND(REPORT_001)는 신고 대상 없음 — 신고 레코드 자체 없음은 RESOURCE_NOT_FOUND
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!report.getReporterId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return MyReportResponse.of(report);
    }

    // ── KAN-93: 자동 숨김 ─────────────────────────────────────────────────

    /**
     * 누적 신고 수가 AUTO_HIDE_THRESHOLD 이상이면 콘텐츠 자동 숨김.
     * USER·MERCHANT·REVIEW는 자동 조치 생략 — 계정 정지·가게 비공개는 관리자가 직접 판단.
     * 이미 삭제된 콘텐츠는 ifPresent 조건으로 중복 delete 방지.
     */
    private void autoHideIfThresholdReached(ReportTargetType targetType, Long targetId) {
        long count = reportRepository.countByTargetTypeAndTargetId(targetType, targetId);
        if (count < AUTO_HIDE_THRESHOLD) return;

        switch (targetType) {
            case POST_COMPANION -> companionPostRepository.findById(targetId)
                    .filter(p -> p.getStatus() == CompanionPostStatus.ACTIVE
                            || p.getStatus() == CompanionPostStatus.CLOSED)
                    .ifPresent(CompanionPost::delete);
            case POST_FREE -> freePostRepository.findById(targetId)
                    .filter(p -> p.getStatus() == FreePostStatus.ACTIVE)
                    .ifPresent(FreePost::delete);
            case COMMENT -> commentRepository.findById(targetId)
                    .filter(c -> c.getStatus() != CommentStatus.DELETED)
                    .ifPresent(Comment::delete);
            case USER, MERCHANT, REVIEW -> {
                // 자동 조치 생략 — 관리자 수동 처리 필요
            }
        }
    }

    // ── 내부 유틸 ──────────────────────────────────────────────────────────

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
            case REVIEW -> {
                // TODO: 리뷰 도메인 구현 후 ReviewRepository 주입하여 존재 검증 추가
                throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND);
            }
        }
    }
}
