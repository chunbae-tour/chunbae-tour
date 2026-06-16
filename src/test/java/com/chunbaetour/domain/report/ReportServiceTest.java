package com.chunbaetour.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.sql.SQLException;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.comment.entity.CommentStatus;
import com.chunbaetour.domain.community.common.PostType;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import com.chunbaetour.domain.report.dto.MyReportResponse;
import com.chunbaetour.domain.report.dto.response.ReportResponse;
import org.springframework.dao.DataIntegrityViolationException;
import com.chunbaetour.domain.report.dto.ReportCreateRequest;
import com.chunbaetour.domain.report.dto.ReportCreateResponse;
import com.chunbaetour.domain.report.dto.response.ReportDetailResponse;
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.event.ReportContentActionEvent;
import com.chunbaetour.domain.report.dto.request.ReportStatusUpdateRequest;
import com.chunbaetour.domain.report.dto.response.PendingCountResponse;
import com.chunbaetour.domain.place.PlaceReview;
import com.chunbaetour.domain.place.PlaceReviewStatus;
import com.chunbaetour.domain.place.repository.PlaceReviewRepository;
import com.chunbaetour.domain.report.repository.ReportRepository;
import com.chunbaetour.domain.report.repository.ReportQueryRepository;
import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import com.chunbaetour.domain.report.entity.SanctionType;
import com.chunbaetour.domain.report.entity.UserSanction;
import com.chunbaetour.domain.report.service.ReportService;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.report.dto.request.ReportResolveRequest;
import com.chunbaetour.domain.report.type.ReportAction;
import com.chunbaetour.domain.shop.service.ShopService;
import org.springframework.context.ApplicationEventPublisher;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService 단위 테스트 (KAN-93)")
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private CompanionPostRepository companionPostRepository;
    @Mock private FreePostRepository freePostRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private PlaceReviewRepository placeReviewRepository;
    @Mock private ReportQueryRepository reportQueryRepository;
    @Mock private UserSanctionRepository userSanctionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ShopService shopService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Spy private java.time.Clock clock = java.time.Clock.systemUTC();

    @InjectMocks
    private ReportService reportService;

    private static final Long REPORTER_ID  = 1L;
    private static final Long OTHER_ID     = 999L;
    private static final Long FREE_POST_ID = 10L;
    private static final Long COMP_POST_ID = 20L;
    private static final Long REPORT_ID    = 100L;
    private static final Long COMMENT_ID      = 30L;
    private static final Long USER_TARGET_ID  = 40L;
    private static final Long MERCHANT_ID     = 50L;
    private static final Long REVIEW_ID       = 60L;
    private static final Long ADMIN_ID       = 200L;

    private FreePost       activeFreePost;
    private CompanionPost  activeCompanionPost;
    private Report         pendingReport;

    @BeforeEach
    void setUp() {
        // @Value 필드는 @InjectMocks로 주입 안 됨 — 테스트용 기본값(3) 직접 세팅
        ReflectionTestUtils.setField(reportService, "autoHideThreshold", 3);

        activeFreePost = FreePost.create(2L, "여행 후기", "좋았어요", List.of());
        ReflectionTestUtils.setField(activeFreePost, "id", FREE_POST_ID);

        activeCompanionPost = CompanionPost.create(
                2L, "동행 구함", "같이 가요", 1L, "제주도", "제주",
                LocalDate.of(2026, 8, 1), 4);
        ReflectionTestUtils.setField(activeCompanionPost, "id", COMP_POST_ID);

        pendingReport = Report.create(REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID,
                ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(pendingReport, "id", REPORT_ID);
    }

    // ── create: 기본 시나리오 ──────────────────────────────────────────────

    @Test
    @DisplayName("정상 신고 생성 → PENDING 상태 반환")
    void create_정상() {
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.of(activeFreePost));
        // 락 획득 후 내부 count 재확인 — findByIdForUpdate 항상 호출됨
        given(freePostRepository.findByIdForUpdate(FREE_POST_ID)).willReturn(Optional.of(activeFreePost));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID)).willReturn(false);
        given(reportRepository.saveAndFlush(any())).willReturn(pendingReport);
        given(reportRepository.countByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.POST_FREE, FREE_POST_ID, ReportStatus.PENDING)).willReturn(1L);

        ReportCreateResponse response = reportService.create(REPORTER_ID,
                new ReportCreateRequest(ReportTargetType.POST_FREE, FREE_POST_ID, ReportReason.SPAM, null));

        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(response.targetType()).isEqualTo(ReportTargetType.POST_FREE);
    }

    @Test
    @DisplayName("USER 자기신고 → REPORT_SELF (DB 조회 없이 즉시 차단)")
    void create_USER_자기신고() {
        assertThatThrownBy(() ->
                reportService.create(REPORTER_ID,
                        new ReportCreateRequest(ReportTargetType.USER, REPORTER_ID, ReportReason.SPAM, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_SELF);
    }

    @Test
    @DisplayName("MERCHANT 신고 — shopId가 reporterId와 숫자 일치해도 owner 다르면 정상 신고 (KAN-93 조기 차단 오판 회귀)")
    void create_MERCHANT_shopId_숫자일치_owner_다름() {
        Long shopId = REPORTER_ID; // shopId(1) == reporterId(1) — 숫자 우연 일치
        Report merchantReport = Report.create(REPORTER_ID, ReportTargetType.MERCHANT, shopId, ReportReason.SPAM, null, null);
        given(shopService.findMerchantAccountId(shopId)).willReturn(Optional.of(OTHER_ID));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                REPORTER_ID, ReportTargetType.MERCHANT, shopId)).willReturn(false);
        given(reportRepository.saveAndFlush(any())).willReturn(merchantReport);

        ReportCreateResponse response = reportService.create(REPORTER_ID,
                new ReportCreateRequest(ReportTargetType.MERCHANT, shopId, ReportReason.SPAM, null));

        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("MERCHANT 신고 — 가게 없음 → REPORT_TARGET_NOT_FOUND")
    void create_MERCHANT_가게없음() {
        // targetId = shopId — 가게 없으면 REPORT_TARGET_NOT_FOUND
        Long shopId = 50L;
        given(shopService.findMerchantAccountId(shopId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                reportService.create(REPORTER_ID,
                        new ReportCreateRequest(ReportTargetType.MERCHANT, shopId, ReportReason.SPAM, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_TARGET_NOT_FOUND);
    }

    @Test
    @DisplayName("MERCHANT 신고 — 본인 소유 가게 → REPORT_SELF (shopId ≠ reporterId이지만 owner가 본인)")
    void create_MERCHANT_본인가게신고() {
        // shopId(50) != reporterId(1) → 조기 차단 통과
        // 가게 owner == reporter → validateReportTarget에서 REPORT_SELF
        Long shopId = 50L;
        given(shopService.findMerchantAccountId(shopId)).willReturn(Optional.of(REPORTER_ID));

        assertThatThrownBy(() ->
                reportService.create(REPORTER_ID,
                        new ReportCreateRequest(ReportTargetType.MERCHANT, shopId, ReportReason.SPAM, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_SELF);
    }

    @Test
    @DisplayName("중복 신고 → DUPLICATE_REPORT")
    void create_중복신고() {
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.of(activeFreePost));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID)).willReturn(true);

        assertThatThrownBy(() ->
                reportService.create(REPORTER_ID,
                        new ReportCreateRequest(ReportTargetType.POST_FREE, FREE_POST_ID, ReportReason.SPAM, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_REPORT);
    }

    @Test
    @DisplayName("신고 대상 없음 → REPORT_TARGET_NOT_FOUND")
    void create_대상없음() {
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                reportService.create(REPORTER_ID,
                        new ReportCreateRequest(ReportTargetType.POST_FREE, FREE_POST_ID, ReportReason.SPAM, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_TARGET_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 게시글 신고 → REPORT_TARGET_INACTIVE")
    void create_비활성_대상() {
        FreePost deletedPost = FreePost.create(2L, "삭제됨", "내용", List.of());
        deletedPost.delete();
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.of(deletedPost));

        assertThatThrownBy(() ->
                reportService.create(REPORTER_ID,
                        new ReportCreateRequest(ReportTargetType.POST_FREE, FREE_POST_ID, ReportReason.SPAM, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_TARGET_INACTIVE);
    }

    // ── create: 자동 숨김 (KAN-93) ────────────────────────────────────────

    @Test
    @DisplayName("신고 2건 — 임계값 미달 시 게시글 상태 변경 없음")
    void create_autoHide_임계값_미달() {
        // 락 획득 후 내부에서 count 재확인 — findByIdForUpdate는 항상 호출됨
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.of(activeFreePost));
        given(freePostRepository.findByIdForUpdate(FREE_POST_ID)).willReturn(Optional.of(activeFreePost));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(any(), any(), any())).willReturn(false);
        given(reportRepository.saveAndFlush(any())).willReturn(pendingReport);
        given(reportRepository.countByTargetTypeAndTargetIdAndStatus(ReportTargetType.POST_FREE, FREE_POST_ID, ReportStatus.PENDING))
                .willReturn(2L);  // 임계값(3) 미달 → 내부 filter 탈락, hide() 미호출

        reportService.create(REPORTER_ID,
                new ReportCreateRequest(ReportTargetType.POST_FREE, FREE_POST_ID, ReportReason.SPAM, null));

        assertThat(activeFreePost.getStatus()).isEqualTo(FreePostStatus.ACTIVE);
    }

    @Test
    @DisplayName("신고 3건 도달 — FreePost 자동 숨김 (HIDDEN)")
    void create_autoHide_FreePost_3건_도달() {
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.of(activeFreePost));
        given(freePostRepository.findByIdForUpdate(FREE_POST_ID)).willReturn(Optional.of(activeFreePost));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(any(), any(), any())).willReturn(false);
        given(reportRepository.saveAndFlush(any())).willReturn(pendingReport);
        given(reportRepository.countByTargetTypeAndTargetIdAndStatus(ReportTargetType.POST_FREE, FREE_POST_ID, ReportStatus.PENDING))
                .willReturn(3L);

        reportService.create(REPORTER_ID,
                new ReportCreateRequest(ReportTargetType.POST_FREE, FREE_POST_ID, ReportReason.SPAM, null));

        assertThat(activeFreePost.getStatus()).isEqualTo(FreePostStatus.HIDDEN);
    }

    @Test
    @DisplayName("신고 3건 도달 — CompanionPost 자동 숨김 (HIDDEN)")
    void create_autoHide_CompanionPost_3건_도달() {
        Report companionReport = Report.create(REPORTER_ID, ReportTargetType.POST_COMPANION,
                COMP_POST_ID, ReportReason.SPAM, null, null);
        given(companionPostRepository.findById(COMP_POST_ID)).willReturn(Optional.of(activeCompanionPost));
        given(companionPostRepository.findByIdForUpdate(COMP_POST_ID)).willReturn(Optional.of(activeCompanionPost));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(any(), any(), any())).willReturn(false);
        given(reportRepository.saveAndFlush(any())).willReturn(companionReport);
        given(reportRepository.countByTargetTypeAndTargetIdAndStatus(ReportTargetType.POST_COMPANION, COMP_POST_ID, ReportStatus.PENDING))
                .willReturn(3L);

        reportService.create(REPORTER_ID,
                new ReportCreateRequest(ReportTargetType.POST_COMPANION, COMP_POST_ID, ReportReason.SPAM, null));

        assertThat(activeCompanionPost.getStatus()).isEqualTo(CompanionPostStatus.HIDDEN);
    }

    @Test
    @DisplayName("POST_FREE 자기신고 → REPORT_SELF")
    void create_POST_FREE_자기신고() {
        FreePost myPost = FreePost.create(REPORTER_ID, "내 글", "내용", List.of());
        ReflectionTestUtils.setField(myPost, "id", FREE_POST_ID);
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.of(myPost));

        assertThatThrownBy(() ->
                reportService.create(REPORTER_ID,
                        new ReportCreateRequest(ReportTargetType.POST_FREE, FREE_POST_ID, ReportReason.SPAM, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_SELF);
    }

    @Test
    @DisplayName("DB unique constraint 위반 시 DUPLICATE_REPORT 변환 (동시성)")
    void create_동시성_중복신고() {
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.of(activeFreePost));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID)).willReturn(false);

        DataIntegrityViolationException mockException = mock(DataIntegrityViolationException.class);
        SQLException cause = new SQLException("uk_reports_reporter_target violated");
        given(mockException.getMostSpecificCause()).willReturn(cause);
        given(reportRepository.saveAndFlush(any())).willThrow(mockException);

        assertThatThrownBy(() ->
                reportService.create(REPORTER_ID,
                        new ReportCreateRequest(ReportTargetType.POST_FREE, FREE_POST_ID, ReportReason.SPAM, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_REPORT);
    }

    @Test
    @DisplayName("COMMENT 신고 3건 도달 → 댓글 자동 숨김 (HIDDEN)")
    void create_autoHide_COMMENT_3건_도달() {
        Long commentId = 30L;
        Comment activeComment = Comment.create(FREE_POST_ID, PostType.FREE, 2L, "댓글 내용");
        ReflectionTestUtils.setField(activeComment, "id", commentId);

        Report commentReport = Report.create(REPORTER_ID, ReportTargetType.COMMENT, commentId,
                ReportReason.SPAM, null, null);
        given(commentRepository.findById(commentId)).willReturn(Optional.of(activeComment));
        given(commentRepository.findByIdForUpdate(commentId)).willReturn(Optional.of(activeComment));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(any(), any(), any())).willReturn(false);
        given(reportRepository.saveAndFlush(any())).willReturn(commentReport);
        given(reportRepository.countByTargetTypeAndTargetIdAndStatus(ReportTargetType.COMMENT, commentId, ReportStatus.PENDING))
                .willReturn(3L);

        reportService.create(REPORTER_ID,
                new ReportCreateRequest(ReportTargetType.COMMENT, commentId, ReportReason.SPAM, null));

        assertThat(activeComment.getStatus()).isEqualTo(CommentStatus.HIDDEN);
    }

    // ── getMyReports ──────────────────────────────────────────────────────

    @Test
    @DisplayName("첫 페이지 조회 — cursor null, hasNext false")
    void getMyReports_첫페이지() {
        given(reportRepository.findByReporterIdOrderByIdDesc(any(), any(Pageable.class)))
                .willReturn(List.of(pendingReport));

        CursorPageResponse<MyReportResponse> result = reportService.getMyReports(REPORTER_ID, null, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("다음 페이지 조회 — cursor 존재 시 id 기준 조회")
    void getMyReports_다음페이지() {
        String cursor = CursorUtils.encode(REPORT_ID);
        given(reportRepository.findByReporterIdAndIdLessThanOrderByIdDesc(any(), any(), any(Pageable.class)))
                .willReturn(List.of(pendingReport));

        CursorPageResponse<MyReportResponse> result = reportService.getMyReports(REPORTER_ID, cursor, 20);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("size+1 반환 시 hasNext true, nextCursor 반환")
    void getMyReports_hasNext() {
        Report report2 = Report.create(REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID,
                ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(report2, "id", 99L);

        given(reportRepository.findByReporterIdOrderByIdDesc(any(), any(Pageable.class)))
                .willReturn(List.of(pendingReport, report2)); // size=1 요청에 2개 반환 → hasNext

        CursorPageResponse<MyReportResponse> result = reportService.getMyReports(REPORTER_ID, null, 1);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
        assertThat(result.content()).hasSize(1);
    }

    // ── getMyReport ───────────────────────────────────────────────────────

    @Test
    @DisplayName("본인 신고 단건 조회 정상")
    void getMyReport_본인신고() {
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(pendingReport));

        MyReportResponse result = reportService.getMyReport(REPORT_ID, REPORTER_ID);

        assertThat(result.reportId()).isEqualTo(REPORT_ID);
        assertThat(result.status()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("타인 신고 조회 → REPORT_NOT_FOUND (enumeration 차단)")
    void getMyReport_타인신고() {
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(pendingReport));

        assertThatThrownBy(() -> reportService.getMyReport(REPORT_ID, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 신고 조회 → REPORT_NOT_FOUND")
    void getMyReport_없는신고() {
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getMyReport(REPORT_ID, REPORTER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }

    // ── getReport (관리자 상세) ────────────────────────────────────────────

    @Test
    @DisplayName("POST_FREE 신고 상세 — targetTitle·targetContent·targetImageUrls 반환")
    void getReport_FREE_POST_상세() {
        FreePost freePostWithImage = FreePost.create(2L, "스팸 제목", "스팸 본문", List.of("https://img.example.com/1.jpg"));
        ReflectionTestUtils.setField(freePostWithImage, "id", FREE_POST_ID);

        Account reporter = mock(Account.class);
        given(reporter.getNickname()).willReturn("신고자닉네임");

        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(pendingReport));
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.of(freePostWithImage));
        given(accountRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));

        ReportDetailResponse result = reportService.getReport(REPORT_ID);

        assertThat(result.reportId()).isEqualTo(REPORT_ID);
        assertThat(result.targetTitle()).isEqualTo("스팸 제목");
        assertThat(result.targetContent()).isEqualTo("스팸 본문");
        assertThat(result.targetImageUrls()).containsExactly("https://img.example.com/1.jpg");
        assertThat(result.reporterNickname()).isEqualTo("신고자닉네임");
    }

    @Test
    @DisplayName("POST_COMPANION 신고 상세 — targetTitle 반환, targetImageUrls null")
    void getReport_COMPANION_POST_상세() {
        Report companionReport = Report.create(REPORTER_ID, ReportTargetType.POST_COMPANION,
                COMP_POST_ID, ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(companionReport, "id", REPORT_ID);

        Account reporter = mock(Account.class);
        given(reporter.getNickname()).willReturn("신고자닉네임");

        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(companionReport));
        given(companionPostRepository.findById(COMP_POST_ID)).willReturn(Optional.of(activeCompanionPost));
        given(accountRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));

        ReportDetailResponse result = reportService.getReport(REPORT_ID);

        assertThat(result.targetTitle()).isEqualTo("동행 구함");
        assertThat(result.targetContent()).isEqualTo("같이 가요");
        assertThat(result.targetImageUrls()).isNull();
    }

    @Test
    @DisplayName("신고 대상 삭제된 경우 — targetTitle·targetContent '(삭제됨)' 반환")
    void getReport_대상_삭제됨_graceful() {
        Account reporter = mock(Account.class);
        given(reporter.getNickname()).willReturn("신고자닉네임");

        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(pendingReport));
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.empty());
        given(accountRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));

        ReportDetailResponse result = reportService.getReport(REPORT_ID);

        assertThat(result.targetTitle()).isEqualTo("(삭제됨)");
        assertThat(result.targetContent()).isEqualTo("(삭제됨)");
        assertThat(result.targetImageUrls()).isNull();
    }

    @Test
    @DisplayName("POST_COMPANION 신고 대상 삭제된 경우 — targetTitle·targetContent '(삭제됨)' 반환")
    void getReport_COMPANION_삭제됨_graceful() {
        Report companionReport = Report.create(REPORTER_ID, ReportTargetType.POST_COMPANION,
                COMP_POST_ID, ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(companionReport, "id", REPORT_ID);

        Account reporter = mock(Account.class);
        given(reporter.getNickname()).willReturn("신고자닉네임");

        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(companionReport));
        given(companionPostRepository.findById(COMP_POST_ID)).willReturn(Optional.empty());
        given(accountRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));

        ReportDetailResponse result = reportService.getReport(REPORT_ID);

        assertThat(result.targetTitle()).isEqualTo("(삭제됨)");
        assertThat(result.targetContent()).isEqualTo("(삭제됨)");
        assertThat(result.targetImageUrls()).isNull();
    }

    @Test
    @DisplayName("COMMENT 신고 대상 삭제된 경우 — targetTitle·targetContent '(삭제됨)' 반환")
    void getReport_COMMENT_삭제됨_graceful() {
        Report commentReport = Report.create(REPORTER_ID, ReportTargetType.COMMENT,
                COMMENT_ID, ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(commentReport, "id", REPORT_ID);

        Account reporter = mock(Account.class);
        given(reporter.getNickname()).willReturn("신고자닉네임");

        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(commentReport));
        given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.empty());
        given(accountRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));

        ReportDetailResponse result = reportService.getReport(REPORT_ID);

        assertThat(result.targetTitle()).isEqualTo("(삭제됨)");
        assertThat(result.targetContent()).isEqualTo("(삭제됨)");
        assertThat(result.targetImageUrls()).isNull();
    }

    @Test
    @DisplayName("USER 신고 대상 삭제된 경우 — targetTitle·targetContent '(삭제됨)' 반환")
    void getReport_USER_삭제됨_graceful() {
        Report userReport = Report.create(REPORTER_ID, ReportTargetType.USER,
                USER_TARGET_ID, ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(userReport, "id", REPORT_ID);

        Account reporter = mock(Account.class);
        given(reporter.getNickname()).willReturn("신고자닉네임");

        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(userReport));
        given(accountRepository.findById(USER_TARGET_ID)).willReturn(Optional.empty());
        given(accountRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));

        ReportDetailResponse result = reportService.getReport(REPORT_ID);

        assertThat(result.targetTitle()).isEqualTo("(삭제됨)");
        assertThat(result.targetContent()).isEqualTo("(삭제됨)");
        assertThat(result.targetImageUrls()).isNull();
    }

    @Test
    @DisplayName("MERCHANT 신고 대상 삭제된 경우 — targetTitle·targetContent '(삭제됨)' 반환")
    void getReport_MERCHANT_삭제됨_graceful() {
        Report merchantReport = Report.create(REPORTER_ID, ReportTargetType.MERCHANT,
                MERCHANT_ID, ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(merchantReport, "id", REPORT_ID);

        Account reporter = mock(Account.class);
        given(reporter.getNickname()).willReturn("신고자닉네임");

        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(merchantReport));
        given(shopService.findMerchantAccountId(MERCHANT_ID)).willReturn(Optional.empty());
        given(accountRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));

        ReportDetailResponse result = reportService.getReport(REPORT_ID);

        assertThat(result.targetTitle()).isEqualTo("(삭제됨)");
        assertThat(result.targetContent()).isEqualTo("(삭제됨)");
        assertThat(result.targetImageUrls()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 신고 관리자 상세 조회 → REPORT_NOT_FOUND")
    void getReport_없는신고() {
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getReport(REPORT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }

    // ── resolveReport: 콘텐츠 액션 이벤트 발행 (PR5 — 동기 처리→이벤트 기반 전환) ──────
    // 실제 콘텐츠 숨김/삭제/정지 및 이미삭제 가드·멱등 처리는 각 도메인 Listener 책임.
    // resolveReport 책임은 ReportContentActionEvent 발행까지 → 여기서 그것만 검증.

    @Test
    @DisplayName("POST_FREE DELETE resolve — ReportContentActionEvent(DELETE) 발행")
    void resolveReport_POST_FREE_DELETE_이벤트발행() {
        Report report = Report.create(REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID,
                ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));

        ReportResolveRequest request = new ReportResolveRequest(ReportAction.DELETE, null);
        reportService.resolveReport(REPORT_ID, ADMIN_ID, request);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        then(eventPublisher).should().publishEvent(new ReportContentActionEvent(
                REPORT_ID, ReportTargetType.POST_FREE, FREE_POST_ID, ReportAction.DELETE));
    }

    @Test
    @DisplayName("POST_COMPANION DELETE resolve — ReportContentActionEvent(DELETE) 발행")
    void resolveReport_POST_COMPANION_DELETE_이벤트발행() {
        Report report = Report.create(REPORTER_ID, ReportTargetType.POST_COMPANION, COMP_POST_ID,
                ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));

        ReportResolveRequest request = new ReportResolveRequest(ReportAction.DELETE, null);
        reportService.resolveReport(REPORT_ID, ADMIN_ID, request);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        then(eventPublisher).should().publishEvent(new ReportContentActionEvent(
                REPORT_ID, ReportTargetType.POST_COMPANION, COMP_POST_ID, ReportAction.DELETE));
    }

    @Test
    @DisplayName("COMMENT DELETE resolve — ReportContentActionEvent(DELETE) 발행")
    void resolveReport_COMMENT_DELETE_이벤트발행() {
        Report report = Report.create(REPORTER_ID, ReportTargetType.COMMENT, COMMENT_ID,
                ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));

        ReportResolveRequest request = new ReportResolveRequest(ReportAction.DELETE, null);
        reportService.resolveReport(REPORT_ID, ADMIN_ID, request);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        then(eventPublisher).should().publishEvent(new ReportContentActionEvent(
                REPORT_ID, ReportTargetType.COMMENT, COMMENT_ID, ReportAction.DELETE));
    }

    @Test
    @DisplayName("USER SUSPEND resolve — ReportContentActionEvent(SUSPEND) 발행")
    void resolveReport_USER_SUSPEND_이벤트발행() {
        Report report = Report.create(REPORTER_ID, ReportTargetType.USER, USER_TARGET_ID,
                ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));

        ReportResolveRequest request = new ReportResolveRequest(ReportAction.SUSPEND, null);
        reportService.resolveReport(REPORT_ID, ADMIN_ID, request);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        then(eventPublisher).should().publishEvent(new ReportContentActionEvent(
                REPORT_ID, ReportTargetType.USER, USER_TARGET_ID, ReportAction.SUSPEND));
    }

    @Test
    @DisplayName("RESTORE resolve — /resolve 부적합 action → REPORT_WRONG_ENDPOINT (정정 전용)")
    void resolveReport_RESTORE_거부() {
        Report report = Report.create(REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID,
                ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));

        ReportResolveRequest request = new ReportResolveRequest(ReportAction.RESTORE, null);

        assertThatThrownBy(() -> reportService.resolveReport(REPORT_ID, ADMIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_WRONG_ENDPOINT);
        then(eventPublisher).shouldHaveNoInteractions();
    }

    // ── REVIEW 신고 (KAN-152 통합) ────────────────────────────────────────

    @Test
    @DisplayName("REVIEW 정상 신고 생성 → PENDING 반환")
    void create_REVIEW_정상() {
        Account author = mock(Account.class);
        given(author.getId()).willReturn(2L);
        PlaceReview review = mock(PlaceReview.class);
        given(review.getStatus()).willReturn(PlaceReviewStatus.ACTIVE);
        given(review.isOwnedBy(REPORTER_ID)).willReturn(false);
        given(review.getAuthor()).willReturn(author);
        given(placeReviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));
        given(placeReviewRepository.findByIdForUpdate(REVIEW_ID)).willReturn(Optional.of(review));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                REPORTER_ID, ReportTargetType.REVIEW, REVIEW_ID)).willReturn(false);
        Report reviewReport = Report.create(REPORTER_ID, ReportTargetType.REVIEW, REVIEW_ID,
                ReportReason.SPAM, null, 2L);
        ReflectionTestUtils.setField(reviewReport, "id", REPORT_ID);
        given(reportRepository.saveAndFlush(any())).willReturn(reviewReport);
        given(reportRepository.countByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.REVIEW, REVIEW_ID, ReportStatus.PENDING)).willReturn(1L);

        ReportCreateResponse response = reportService.create(REPORTER_ID,
                new ReportCreateRequest(ReportTargetType.REVIEW, REVIEW_ID, ReportReason.SPAM, null));

        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(response.targetType()).isEqualTo(ReportTargetType.REVIEW);
    }

    @Test
    @DisplayName("삭제된 리뷰 신고 → REPORT_TARGET_INACTIVE")
    void create_REVIEW_비활성() {
        PlaceReview review = mock(PlaceReview.class);
        given(review.getStatus()).willReturn(PlaceReviewStatus.DELETED);
        given(placeReviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));

        assertThatThrownBy(() ->
                reportService.create(REPORTER_ID,
                        new ReportCreateRequest(ReportTargetType.REVIEW, REVIEW_ID, ReportReason.SPAM, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_TARGET_INACTIVE);
    }

    @Test
    @DisplayName("자기 리뷰 신고 → REPORT_SELF")
    void create_REVIEW_자기신고() {
        PlaceReview review = mock(PlaceReview.class);
        given(review.getStatus()).willReturn(PlaceReviewStatus.ACTIVE);
        given(review.isOwnedBy(REPORTER_ID)).willReturn(true);
        given(placeReviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));

        assertThatThrownBy(() ->
                reportService.create(REPORTER_ID,
                        new ReportCreateRequest(ReportTargetType.REVIEW, REVIEW_ID, ReportReason.SPAM, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_SELF);
    }

    @Test
    @DisplayName("REVIEW 신고 3건 도달 — 리뷰 자동 숨김 (HIDDEN)")
    void create_REVIEW_autoHide() {
        Account author = mock(Account.class);
        given(author.getId()).willReturn(2L);
        PlaceReview review = mock(PlaceReview.class);
        given(review.getStatus()).willReturn(PlaceReviewStatus.ACTIVE);
        given(review.isOwnedBy(REPORTER_ID)).willReturn(false);
        given(review.getAuthor()).willReturn(author);
        given(placeReviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));
        given(placeReviewRepository.findByIdForUpdate(REVIEW_ID)).willReturn(Optional.of(review));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                REPORTER_ID, ReportTargetType.REVIEW, REVIEW_ID)).willReturn(false);
        Report reviewReport = Report.create(REPORTER_ID, ReportTargetType.REVIEW, REVIEW_ID,
                ReportReason.SPAM, null, 2L);
        ReflectionTestUtils.setField(reviewReport, "id", REPORT_ID);
        given(reportRepository.saveAndFlush(any())).willReturn(reviewReport);
        given(reportRepository.countByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.REVIEW, REVIEW_ID, ReportStatus.PENDING)).willReturn(3L);

        reportService.create(REPORTER_ID,
                new ReportCreateRequest(ReportTargetType.REVIEW, REVIEW_ID, ReportReason.SPAM, null));

        then(review).should().hide();
    }

    @Test
    @DisplayName("REVIEW 신고 상세 — targetContent 반환")
    void getReport_REVIEW_상세() {
        Report reviewReport = Report.create(REPORTER_ID, ReportTargetType.REVIEW, REVIEW_ID,
                ReportReason.SPAM, null, 2L);
        ReflectionTestUtils.setField(reviewReport, "id", REPORT_ID);
        PlaceReview review = mock(PlaceReview.class);
        given(review.getContent()).willReturn("리뷰 본문");
        Account reporter = mock(Account.class);
        given(reporter.getNickname()).willReturn("신고자닉네임");

        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(reviewReport));
        given(placeReviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));
        given(accountRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));
        given(accountRepository.findById(2L)).willReturn(Optional.empty());

        ReportDetailResponse result = reportService.getReport(REPORT_ID);

        assertThat(result.targetTitle()).isNull();
        assertThat(result.targetContent()).isEqualTo("리뷰 본문");
    }

    @Test
    @DisplayName("REVIEW 신고 대상 삭제된 경우 — '(삭제됨)' 반환")
    void getReport_REVIEW_삭제됨() {
        Report reviewReport = Report.create(REPORTER_ID, ReportTargetType.REVIEW, REVIEW_ID,
                ReportReason.SPAM, null, 2L);
        ReflectionTestUtils.setField(reviewReport, "id", REPORT_ID);
        Account reporter = mock(Account.class);
        given(reporter.getNickname()).willReturn("신고자닉네임");

        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(reviewReport));
        given(placeReviewRepository.findById(REVIEW_ID)).willReturn(Optional.empty());
        given(accountRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));
        given(accountRepository.findById(2L)).willReturn(Optional.empty());

        ReportDetailResponse result = reportService.getReport(REPORT_ID);

        assertThat(result.targetContent()).isEqualTo("(삭제됨)");
    }

    // ── 관리자 목록 필터 + 제재 뱃지 ────────────────────────────────────────

    @Test
    @DisplayName("관리자 목록 — 필터 전달 + 피신고 유저 제재 뱃지(계정 레벨) 매핑")
    void getReports_filter_and_sanction_badge() {
        Report report = Report.create(REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID,
                ReportReason.SPAM, null, USER_TARGET_ID);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        given(reportQueryRepository.findByFilter(
                eq(ReportStatus.RESOLVED), eq(ReportTargetType.POST_FREE), eq(ReportReason.SPAM),
                eq(USER_TARGET_ID), isNull(), eq(20)))
                .willReturn(java.util.List.of(report));
        Account reporter = mock(Account.class);
        given(reporter.getId()).willReturn(REPORTER_ID);
        given(reporter.getNickname()).willReturn("신고자");
        Account reportedUser = mock(Account.class);
        given(reportedUser.getId()).willReturn(USER_TARGET_ID);
        given(reportedUser.getStatus()).willReturn(AccountStatus.SUSPENDED);
        given(reportedUser.getSanctionType()).willReturn(SanctionType.SUSPEND_30D);
        given(accountRepository.findAllById(any())).willReturn(java.util.List.of(reporter, reportedUser));

        CursorPageResponse<ReportResponse> result = reportService.getReports(
                "RESOLVED", ReportTargetType.POST_FREE, ReportReason.SPAM, USER_TARGET_ID, null, 20);

        assertThat(result.content()).hasSize(1);
        ReportResponse r = result.content().get(0);
        assertThat(r.reporterNickname()).isEqualTo("신고자");
        assertThat(r.reportedUserStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(r.reportedUserSanctionType()).isEqualTo(SanctionType.SUSPEND_30D);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("관리자 목록 — reportedUserId null이면 제재 뱃지 null")
    void getReports_no_reportedUser_null_badge() {
        Report report = Report.create(REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID,
                ReportReason.SPAM, null, null);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        given(reportQueryRepository.findByFilter(isNull(), isNull(), isNull(), isNull(), isNull(), eq(20)))
                .willReturn(java.util.List.of(report));
        Account reporter = mock(Account.class);
        given(reporter.getId()).willReturn(REPORTER_ID);
        given(reporter.getNickname()).willReturn("신고자");
        given(accountRepository.findAllById(any())).willReturn(java.util.List.of(reporter));

        CursorPageResponse<ReportResponse> result = reportService.getReports(
                null, null, null, null, null, 20);

        ReportResponse r = result.content().get(0);
        assertThat(r.reportedUserStatus()).isNull();
        assertThat(r.reportedUserSanctionType()).isNull();
    }

    // ── 미처리 신고 건수 (PR6) ────────────────────────────────────────────

    @Test
    @DisplayName("신고 상세 — 피신고 유저 제재 상태(계정+도메인) 포함")
    void getReport_includes_reportedUserSanction() {
        Report report = Report.create(REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID,
                ReportReason.SPAM, null, USER_TARGET_ID);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        Account reporter = mock(Account.class);
        given(reporter.getNickname()).willReturn("신고자");
        Account reportedUser = mock(Account.class);
        given(reportedUser.getStatus()).willReturn(AccountStatus.SUSPENDED);
        given(reportedUser.getSanctionType()).willReturn(SanctionType.SUSPEND_30D);
        given(reportedUser.getSanctionEndAt()).willReturn(java.time.LocalDateTime.of(2026, 7, 1, 0, 0));
        UserSanction domainSanction = UserSanction.create(USER_TARGET_ID, 1L,
                ReportTargetType.POST_FREE, SanctionType.SUSPEND_7D, "도메인 제재",
                java.time.LocalDateTime.of(2026, 6, 1, 0, 0), java.time.LocalDateTime.of(2026, 6, 8, 0, 0));

        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.empty());
        given(accountRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));
        given(accountRepository.findById(USER_TARGET_ID)).willReturn(Optional.of(reportedUser));
        given(userSanctionRepository.findAllActiveSanctionsByUserId(eq(USER_TARGET_ID), any()))
                .willReturn(java.util.List.of(domainSanction));

        ReportDetailResponse result = reportService.getReport(REPORT_ID);

        assertThat(result.reportedUserSanction()).isNotNull();
        assertThat(result.reportedUserSanction().accountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(result.reportedUserSanction().accountSanctionType()).isEqualTo(SanctionType.SUSPEND_30D);
        assertThat(result.reportedUserSanction().activeDomainSanctions()).hasSize(1);
        assertThat(result.reportedUserSanction().activeDomainSanctions().get(0).targetType())
                .isEqualTo(ReportTargetType.POST_FREE);
    }

    @Test
    @DisplayName("getPendingCount — PENDING 건수 반환")
    void getPendingCount_returns_count() {
        given(reportRepository.countByStatus(ReportStatus.PENDING)).willReturn(7L);

        PendingCountResponse result = reportService.getPendingCount();

        assertThat(result.count()).isEqualTo(7L);
    }

    // ── 제재 카운트 1년 윈도우 ──────────────────────────────────────────────

    @Test
    @DisplayName("제재 카운트 — 1년 윈도우 메서드로 집계 + ReportAcceptedEvent 발행")
    void resolveReport_uses_windowed_count() {
        Report report = Report.create(REPORTER_ID, ReportTargetType.USER, USER_TARGET_ID,
                ReportReason.SPAM, null, USER_TARGET_ID);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        given(reportRepository.countByReportedUserIdAndTargetTypeAndStatusAndResolvedAtGreaterThanEqual(
                eq(USER_TARGET_ID), eq(ReportTargetType.USER), eq(ReportStatus.RESOLVED), any()))
                .willReturn(5L);

        reportService.resolveReport(REPORT_ID, ADMIN_ID,
                new ReportResolveRequest(ReportAction.SUSPEND, null));

        // 윈도우 카운트(5)로 ReportAcceptedEvent 발행 — 옛 비윈도우 메서드는 호출 안 됨
        then(reportRepository).should().countByReportedUserIdAndTargetTypeAndStatusAndResolvedAtGreaterThanEqual(
                eq(USER_TARGET_ID), eq(ReportTargetType.USER), eq(ReportStatus.RESOLVED), any());
    }

    // ── 신고 상태 정정 (오판 정정) ──────────────────────────────────────────

    @Test
    @DisplayName("신고 상태 정정 — RESOLVED→DISMISSED + RESTORE 이벤트 발행")
    void updateReportStatus_RESOLVED_to_DISMISSED() {
        Report report = Report.create(REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID,
                ReportReason.SPAM, null, 2L);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        report.resolve(ReportAction.DELETE, "처리함", "admin01", LocalDateTime.now());
        Account admin = mock(Account.class);
        given(admin.getNickname()).willReturn("admin01");
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        given(accountRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));

        reportService.updateReportStatus(REPORT_ID, ADMIN_ID,
                new ReportStatusUpdateRequest(ReportStatus.DISMISSED, "오판 정정"));

        assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        then(reportRepository).should().saveAndFlush(report);
        then(eventPublisher).should().publishEvent(new ReportContentActionEvent(
                REPORT_ID, ReportTargetType.POST_FREE, FREE_POST_ID, ReportAction.RESTORE));
    }

    @Test
    @DisplayName("PENDING 신고 상태 정정 시도 → REPORT_INVALID_STATUS_TRANSITION")
    void updateReportStatus_PENDING_rejected() {
        Report report = Report.create(REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID,
                ReportReason.SPAM, null, 2L);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.updateReportStatus(REPORT_ID, ADMIN_ID,
                new ReportStatusUpdateRequest(ReportStatus.DISMISSED, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("DISMISSED 외 상태로 정정 시도 → REPORT_INVALID_STATUS_TRANSITION")
    void updateReportStatus_nonDismissed_rejected() {
        Report report = Report.create(REPORTER_ID, ReportTargetType.POST_FREE, FREE_POST_ID,
                ReportReason.SPAM, null, 2L);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        report.resolve(ReportAction.DELETE, "처리함", "admin01", LocalDateTime.now());
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.updateReportStatus(REPORT_ID, ADMIN_ID,
                new ReportStatusUpdateRequest(ReportStatus.PENDING, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_INVALID_STATUS_TRANSITION);
    }
}
