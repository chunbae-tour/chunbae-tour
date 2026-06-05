package com.chunbaetour.domain.report.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
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
import com.chunbaetour.domain.shop.service.ShopService;
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
import org.springframework.beans.factory.annotation.Value;
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

    /** n건 이상 신고 시 콘텐츠 자동 숨김 임계값 — application.yml report.auto-hide.threshold (KAN-93) */
    @Value("${report.auto-hide.threshold:3}")
    private int autoHideThreshold;

    private final ReportRepository reportRepository;
    private final AccountRepository accountRepository;
    private final CompanionPostRepository companionPostRepository;
    private final FreePostRepository freePostRepository;
    private final CommentRepository commentRepository;
    private final ShopService shopService;

    // ── KAN-90: 신고 접수 ─────────────────────────────────────────────────

    @Transactional
    public ReportCreateResponse create(Long reporterId, ReportCreateRequest request) {
        // USER 자기신고 — DB 없이 즉시 차단
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
     * 누적 신고 수가 autoHideThreshold 이상이면 콘텐츠 자동 숨김.
     * USER·MERCHANT는 자동 조치 생략 — 계정 정지·가게 비공개는 관리자가 직접 판단.
     * 이미 삭제된 콘텐츠는 filter 조건으로 중복 처리 방지.
     */
    private void autoHideIfThresholdReached(ReportTargetType targetType, Long targetId) {
        // 외부 카운트 체크 제거 — 락 바깥에서 count를 확인하면 동시 트랜잭션이 모두
        // 임계값 미만으로 읽어 아무도 hide를 호출하지 않는 race condition 발생.
        // 락 획득(findByIdForUpdate) 후 내부에서 count를 재확인해 정확히 한 번만 실행.
        switch (targetType) {
            case POST_COMPANION -> companionPostRepository.findByIdForUpdate(targetId)
                    .filter(p -> p.getStatus() == CompanionPostStatus.ACTIVE)
                    .filter(p -> reportRepository.countByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.PENDING) >= autoHideThreshold)
                    .ifPresent(CompanionPost::hide);
            case POST_FREE -> freePostRepository.findByIdForUpdate(targetId)
                    .filter(p -> p.getStatus() == FreePostStatus.ACTIVE)
                    .filter(p -> reportRepository.countByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.PENDING) >= autoHideThreshold)
                    .ifPresent(FreePost::hide);
            case COMMENT -> commentRepository.findByIdForUpdate(targetId)
                    .filter(c -> c.getStatus() != CommentStatus.DELETED)
                    .filter(c -> reportRepository.countByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.PENDING) >= autoHideThreshold)
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

    private void applyMerchantAction(ReportAction action, Long shopId) {
        switch (action) {
            // HIDE_SHOP: 신고 대상 가게만 임시 정지 (SUSPENDED, 복구 가능). role 유지.
            case HIDE_SHOP -> shopService.hideShop(shopId);
            // REVOKE_MERCHANT: 계정 단위 처리 — owner의 모든 가게 SUSPENDED + MERCHANT → USER 권한 회수.
            //   다중 가게 운영 시 신고 대상 외 가게도 모두 정지됨.
            case REVOKE_MERCHANT -> {
                Long ownerId = shopService.findMerchantAccountId(shopId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                shopService.hideAllShopsByOwnerId(ownerId);
                Account owner = accountRepository.findById(ownerId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (owner.getRole() == Role.MERCHANT) {
                    owner.revokeToUser();
                }
            }
            case DISMISS -> { /* 무시, 상태 기록만 */ }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void deleteTargetContent(ReportTargetType targetType, Long targetId) {
        switch (targetType) {
            case POST_COMPANION -> companionPostRepository.findById(targetId).ifPresentOrElse(post -> {
                if (post.getStatus() == CompanionPostStatus.DELETED) {
                    log.warn("deleteTargetContent: POST_COMPANION already DELETED, targetId={}", targetId);
                    return;
                }
                post.hide();
            }, () -> log.warn("deleteTargetContent: POST_COMPANION not found, targetId={}", targetId));
            case POST_FREE -> freePostRepository.findById(targetId).ifPresentOrElse(post -> {
                if (post.getStatus() == FreePostStatus.DELETED) {
                    log.warn("deleteTargetContent: POST_FREE already DELETED, targetId={}", targetId);
                    return;
                }
                post.hide();
            }, () -> log.warn("deleteTargetContent: POST_FREE not found, targetId={}", targetId));
            case COMMENT -> commentRepository.findById(targetId).ifPresentOrElse(
                comment -> comment.delete(),
                () -> log.warn("deleteTargetContent: COMMENT not found, targetId={}", targetId));
            case USER -> accountRepository.findById(targetId).ifPresentOrElse(acc -> {
                if (acc.getStatus() == AccountStatus.DELETED) {
                    log.warn("deleteTargetContent: USER already DELETED(탈퇴), targetId={}", targetId);
                    return;
                }
                acc.suspend();
            }, () -> log.warn("deleteTargetContent: USER not found, targetId={}", targetId));
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
        accountRepository.findById(authorId).ifPresentOrElse(acc -> {
            if (acc.getStatus() == AccountStatus.DELETED) {
                log.warn("suspendTargetAuthor: 탈퇴 계정 정지 생략, authorId={}", authorId);
                return;
            }
            acc.suspend();
        }, () -> log.warn("suspendTargetAuthor: 계정 없음, authorId={}", authorId));
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
                    .map(p -> new TargetDetail(p.getTitle(), p.getContent(),
                            List.copyOf(p.getImageUrls())))
                    .orElse(TargetDetail.deleted());
            case POST_COMPANION -> companionPostRepository.findById(targetId)
                    .map(p -> new TargetDetail(p.getTitle(), p.getContent(), null))
                    .orElse(TargetDetail.deleted());
            case COMMENT -> commentRepository.findById(targetId)
                    .map(c -> new TargetDetail(null, c.getContent(), null))
                    .orElse(TargetDetail.deleted());
            case USER -> accountRepository.findById(targetId)
                    .map(a -> new TargetDetail(null, a.getNickname(), null))
                    .orElse(TargetDetail.deleted());
            // MERCHANT: targetId = shopId → accountId 변환 후 닉네임 조회
            case MERCHANT -> shopService.findMerchantAccountId(targetId)
                    .flatMap(accountRepository::findById)
                    .map(a -> new TargetDetail(null, a.getNickname(), null))
                    .orElse(TargetDetail.deleted());
            // TODO(KAN-152): REVIEW 도메인 구현 후 case 추가 — title·content·imageUrls 반환
        };
    }

    /** 신고 대상 콘텐츠 정보 내부 전달 객체. */
    private record TargetDetail(String title, String content, java.util.List<String> imageUrls) {
        static TargetDetail empty() {
            return new TargetDetail(null, null, null);
        }
        /** 신고 접수 후 대상 콘텐츠가 삭제된 경우 — 관리자 UI에 "(삭제됨)" 표시. */
        static TargetDetail deleted() {
            return new TargetDetail("(삭제됨)", "(삭제됨)", null);
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
                // targetId = shopId — 다중 가게 지원: 신고 대상 가게를 명시적으로 지정
                Long ownerId = shopService.findMerchantAccountId(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (ownerId.equals(reporterId)) {
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
