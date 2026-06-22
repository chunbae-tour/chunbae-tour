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
import com.chunbaetour.domain.place.PlaceReview;
import com.chunbaetour.domain.place.PlaceReviewStatus;
import com.chunbaetour.domain.place.repository.PlaceReviewRepository;
import com.chunbaetour.domain.shop.service.ShopService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Period;
import com.chunbaetour.domain.report.dto.MyReportResponse;
import com.chunbaetour.domain.report.dto.ReportCreateRequest;
import com.chunbaetour.domain.report.dto.ReportCreateResponse;
import com.chunbaetour.domain.report.dto.response.PendingCountResponse;
import com.chunbaetour.domain.report.dto.request.MerchantReportResolveRequest;
import com.chunbaetour.domain.report.dto.request.ReportResolveRequest;
import com.chunbaetour.domain.report.dto.request.ReportStatusUpdateRequest;
import com.chunbaetour.domain.report.dto.response.ReportDetailResponse;
import com.chunbaetour.domain.report.dto.response.ReportResolveResponse;
import com.chunbaetour.domain.report.dto.response.ReportResponse;
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.event.ReportContentActionEvent;
import com.chunbaetour.domain.report.repository.ReportRepository;
import com.chunbaetour.domain.report.repository.ReportQueryRepository;
import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import com.chunbaetour.domain.report.dto.response.ReportedUserSanctionInfo;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.event.ReportAcceptedEvent;
import com.chunbaetour.domain.report.type.ReportAction;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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

    /** 제재 누적 카운트 집계 윈도우 — 최근 1년 RESOLVED 신고만 집계 (오래된 신고 자동 만료). */
    private static final Period SANCTION_COUNT_WINDOW = Period.ofYears(1);

    /** n건 이상 신고 시 콘텐츠 자동 숨김 임계값 — application.yml report.auto-hide.threshold (KAN-93) */
    @Value("${report.auto-hide.threshold:3}")
    private int autoHideThreshold;

    private final ReportRepository reportRepository;
    private final ReportQueryRepository reportQueryRepository;
    private final UserSanctionRepository userSanctionRepository;
    private final AccountRepository accountRepository;
    private final CompanionPostRepository companionPostRepository;
    private final FreePostRepository freePostRepository;
    private final CommentRepository commentRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final ShopService shopService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // ── PR6: 미처리 신고 건수 ────────────────────────────────────────────

    public PendingCountResponse getPendingCount() {
        return PendingCountResponse.of(reportRepository.countByStatus(ReportStatus.PENDING));
    }

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
            Long reportedUserId = resolveReportedUserId(request.targetType(), request.targetId());
            if (reportedUserId == null) {
                throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND);
            }
            Report report = Report.create(
                    reporterId, request.targetType(), request.targetId(),
                    request.reason(), request.description(), reportedUserId);
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

        // 슬라이스·매핑·nextCursor·size echo 공통 진입점 단일화 (KAN-325)
        return CursorPageResponse.of(reports, size, MyReportResponse::of, Report::getId);
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
    public CursorPageResponse<ReportResponse> getReports(String statusParam, ReportTargetType targetType,
            ReportReason reason, Long reportedUserId, String cursor, int size) {
        ReportStatus status = parseStatus(statusParam);
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<Report> reports = reportQueryRepository.findByFilter(
                status, targetType, reason, reportedUserId, cursorId, size);

        boolean hasNext = reports.size() > size;
        List<Report> content = hasNext ? reports.subList(0, size) : reports;
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        // N+1 방지: 신고자 + 피신고 유저 계정 일괄 조회 (제재 상태 뱃지용)
        Set<Long> accountIds = new HashSet<>();
        content.forEach(r -> {
            accountIds.add(r.getReporterId());
            if (r.getReportedUserId() != null) {
                accountIds.add(r.getReportedUserId());
            }
        });
        Map<Long, Account> accountMap = accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, a -> a));

        List<ReportResponse> responses = content.stream()
                .map(r -> {
                    Account reporter = accountMap.get(r.getReporterId());
                    String nickname = reporter != null ? reporter.getNickname() : "탈퇴한 사용자";
                    Account reportedUser = r.getReportedUserId() != null
                            ? accountMap.get(r.getReportedUserId()) : null;
                    return ReportResponse.of(r, nickname, reportedUser);
                })
                .toList();

        // 페이지별 보강(신고자·피신고 계정 일괄)이라 조립형 팩토리 — size echo 통일(responses.size() 아님, KAN-325)
        return CursorPageResponse.ofAssembled(responses, nextCursor, hasNext, size);
    }

    /**
     * 관리자 신고 단건 상세 조회.
     */
    public ReportDetailResponse getReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        TargetDetail detail = resolveTargetDetail(report.getTargetType(), report.getTargetId());
        ReportedUserSanctionInfo sanctionInfo = resolveReportedUserSanction(report.getReportedUserId());
        return ReportDetailResponse.of(report, resolveNickname(report.getReporterId()),
                detail.title(), detail.content(), detail.imageUrls(), sanctionInfo);
    }

    /** 피신고 유저의 현재 제재 상태 (계정 레벨 + 도메인별 활성 제재). 없으면 null. */
    private ReportedUserSanctionInfo resolveReportedUserSanction(Long reportedUserId) {
        if (reportedUserId == null) {
            return null;
        }
        // 제재 활성 판정은 ended_at/released_at(UTC clock 기준)과 비교하므로 now(clock) 사용 (JVM tz 무관)
        return accountRepository.findById(reportedUserId)
                .map(account -> ReportedUserSanctionInfo.of(account,
                        userSanctionRepository.findAllActiveSanctionsByUserId(
                                reportedUserId, LocalDateTime.now(clock))))
                .orElse(null);
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
            // RESTORE는 정정 전용(updateReportStatus)에서만 발행. 최초 처리(/resolve)에서 허용하면
            // 이 신고와 무관하게(다른 신고로 숨긴) 콘텐츠를 복원하는 부작용 → 부적합 action으로 차단.
            if (action == ReportAction.HIDE_SHOP || action == ReportAction.REVOKE_MERCHANT
                    || action == ReportAction.RESTORE) {
                throw new BusinessException(ErrorCode.REPORT_WRONG_ENDPOINT);
            }

            // event 발행: WARNING·DISMISS 제외
            if (action != ReportAction.WARNING && action != ReportAction.DISMISS) {
                eventPublisher.publishEvent(new ReportContentActionEvent(
                        reportId, report.getTargetType(), report.getTargetId(), action));
            }

            String adminNickname = resolveNickname(adminId);
            LocalDateTime now = LocalDateTime.now(clock);
            if (action == ReportAction.DISMISS || action == ReportAction.RESTORE) {
                report.dismiss(request.adminNote(), adminNickname, now);
            } else {
                report.resolve(action, request.adminNote(), adminNickname, now);
            }
            reportRepository.saveAndFlush(report);
            publishReportAcceptedEventIfNeeded(report, action);

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
            LocalDateTime now = LocalDateTime.now(clock);
            if (action == ReportAction.DISMISS) {
                report.dismiss(request.adminNote(), adminNickname, now);
            } else {
                report.resolve(action, request.adminNote(), adminNickname, now);
            }
            reportRepository.saveAndFlush(report);
            publishReportAcceptedEventIfNeeded(report, action);

            return ReportResolveResponse.of(report);
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_RESOLVED);
        }
    }

    /**
     * 신고 상태 정정 (관리자 오판 정정) — RESOLVED → DISMISSED 만 허용.
     *
     * <p>처리된 신고를 오판으로 판단해 기각 처리한다:
     * <ul>
     *   <li>콘텐츠 복원: {@code ReportContentActionEvent(RESTORE)} 발행 → 숨김 콘텐츠 ACTIVE 복구</li>
     *   <li>누적 카운트: status가 DISMISSED가 되어 RESOLVED 집계에서 자동 제외 (유효 신고수 감소)</li>
     *   <li>정지 해제는 자동 연동하지 않음 — 필요 시 관리자가 releaseSanction 별도 수행</li>
     * </ul>
     */
    @Transactional
    public ReportResolveResponse updateReportStatus(Long reportId, Long adminId,
                                                    ReportStatusUpdateRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        // 허용 전이: RESOLVED → DISMISSED 만
        if (!report.isResolved() || request.status() != ReportStatus.DISMISSED) {
            throw new BusinessException(ErrorCode.REPORT_INVALID_STATUS_TRANSITION);
        }

        String adminNickname = resolveNickname(adminId);
        report.correctToDismissed(request.adminNote(), adminNickname, LocalDateTime.now(clock));
        reportRepository.saveAndFlush(report);

        // 콘텐츠 복원 — 기각이므로 숨김 콘텐츠 되살림
        eventPublisher.publishEvent(new ReportContentActionEvent(
                reportId, report.getTargetType(), report.getTargetId(), ReportAction.RESTORE));

        return ReportResolveResponse.of(report);
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
                    .filter(c -> c.getStatus() == CommentStatus.ACTIVE)
                    .filter(c -> reportRepository.countByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.PENDING) >= autoHideThreshold)
                    .ifPresent(Comment::hide);
            case USER, MERCHANT -> {
                // 자동 조치 생략 — 관리자 수동 처리 필요
            }
            case REVIEW -> placeReviewRepository.findByIdForUpdate(targetId)
                    .filter(r -> r.getStatus() == PlaceReviewStatus.ACTIVE)
                    .filter(r -> reportRepository.countByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.PENDING) >= autoHideThreshold)
                    .ifPresent(PlaceReview::hide);
        }
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────

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

    private void publishReportAcceptedEventIfNeeded(Report report, ReportAction action) {
        // DISMISS·RESTORE는 신고를 DISMISSED로 종결(무효 처리) → 제재 카운트에 미집계, 이벤트 미발행.
        if (action == ReportAction.DISMISS || action == ReportAction.RESTORE) return;
        Long reportedUserId = report.getReportedUserId();
        if (reportedUserId == null) {
            reportedUserId = resolveReportedUserId(report.getTargetType(), report.getTargetId());
        }
        if (reportedUserId == null) return;
        // 1년 롤링 윈도우: 최근 1년 RESOLVED 신고만 제재 단계 판정에 집계 (오래된 신고 자동 만료)
        // resolved_at(서비스가 now(clock) 주입, UTC)과 비교하므로 윈도우 시작도 동일 기준 사용
        LocalDateTime windowStart = LocalDateTime.now(clock).minus(SANCTION_COUNT_WINDOW);
        long acceptedCount = reportRepository
                .countByReportedUserIdAndTargetTypeAndStatusAndResolvedAtGreaterThanEqual(
                        reportedUserId, report.getTargetType(), ReportStatus.RESOLVED, windowStart);
        eventPublisher.publishEvent(new ReportAcceptedEvent(
                report.getId(), reportedUserId, report.getTargetType(), acceptedCount));
    }

    private Long resolveReportedUserId(ReportTargetType targetType, Long targetId) {
        return switch (targetType) {
            case POST_COMPANION -> companionPostRepository.findById(targetId)
                    .map(CompanionPost::getAuthorId).orElse(null);
            case POST_FREE -> freePostRepository.findById(targetId)
                    .map(FreePost::getAuthorId).orElse(null);
            case COMMENT -> commentRepository.findById(targetId)
                    .map(Comment::getAuthorId).orElse(null);
            case USER -> targetId;
            case MERCHANT -> shopService.findMerchantAccountId(targetId).orElse(null);
            case REVIEW -> placeReviewRepository.findById(targetId)
                    .map(r -> r.getAuthor().getId()).orElse(null);
            default -> null;
        };
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
            case REVIEW -> placeReviewRepository.findById(targetId)
                    .map(r -> new TargetDetail(null, r.getContent(), null))
                    .orElse(TargetDetail.deleted());
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
                if (account.getStatus() == AccountStatus.DELETED) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
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
            case REVIEW -> {
                PlaceReview review = placeReviewRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                if (review.getStatus() != PlaceReviewStatus.ACTIVE) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
                }
                if (review.isOwnedBy(reporterId)) {
                    throw new BusinessException(ErrorCode.REPORT_SELF);
                }
            }
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
