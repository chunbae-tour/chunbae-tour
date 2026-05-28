package com.chunbaetour.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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
import org.springframework.dao.DataIntegrityViolationException;
import com.chunbaetour.domain.report.dto.ReportCreateRequest;
import com.chunbaetour.domain.report.dto.ReportCreateResponse;
import com.chunbaetour.domain.report.dto.response.ReportDetailResponse;
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.ReportRepository;
import com.chunbaetour.domain.report.service.ReportService;
import com.chunbaetour.domain.shop.service.ShopService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    @Mock private AccountRepository accountRepository;
    @Mock private ShopService shopService;

    @InjectMocks
    private ReportService reportService;

    private static final Long REPORTER_ID  = 1L;
    private static final Long OTHER_ID     = 999L;
    private static final Long FREE_POST_ID = 10L;
    private static final Long COMP_POST_ID = 20L;
    private static final Long REPORT_ID    = 100L;

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
                ReportReason.SPAM, null);
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
        given(reportRepository.countByTargetTypeAndTargetId(
                ReportTargetType.POST_FREE, FREE_POST_ID)).willReturn(1L);

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
        given(shopService.findMerchantAccountId(shopId)).willReturn(Optional.of(OTHER_ID));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                REPORTER_ID, ReportTargetType.MERCHANT, shopId)).willReturn(false);
        given(reportRepository.saveAndFlush(any())).willReturn(pendingReport);
        given(reportRepository.countByTargetTypeAndTargetId(
                ReportTargetType.MERCHANT, shopId)).willReturn(1L);

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
        given(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.POST_FREE, FREE_POST_ID))
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
        given(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.POST_FREE, FREE_POST_ID))
                .willReturn(3L);

        reportService.create(REPORTER_ID,
                new ReportCreateRequest(ReportTargetType.POST_FREE, FREE_POST_ID, ReportReason.SPAM, null));

        assertThat(activeFreePost.getStatus()).isEqualTo(FreePostStatus.HIDDEN);
    }

    @Test
    @DisplayName("신고 3건 도달 — CompanionPost 자동 숨김 (HIDDEN)")
    void create_autoHide_CompanionPost_3건_도달() {
        Report companionReport = Report.create(REPORTER_ID, ReportTargetType.POST_COMPANION,
                COMP_POST_ID, ReportReason.SPAM, null);
        given(companionPostRepository.findById(COMP_POST_ID)).willReturn(Optional.of(activeCompanionPost));
        given(companionPostRepository.findByIdForUpdate(COMP_POST_ID)).willReturn(Optional.of(activeCompanionPost));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(any(), any(), any())).willReturn(false);
        given(reportRepository.saveAndFlush(any())).willReturn(companionReport);
        given(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.POST_COMPANION, COMP_POST_ID))
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
    @DisplayName("COMMENT 신고 3건 도달 → 댓글 자동 삭제")
    void create_autoHide_COMMENT_3건_도달() {
        Long commentId = 30L;
        Comment activeComment = Comment.create(FREE_POST_ID, PostType.FREE, 2L, "댓글 내용");
        ReflectionTestUtils.setField(activeComment, "id", commentId);

        Report commentReport = Report.create(REPORTER_ID, ReportTargetType.COMMENT, commentId,
                ReportReason.SPAM, null);
        given(commentRepository.findById(commentId)).willReturn(Optional.of(activeComment));
        given(commentRepository.findByIdForUpdate(commentId)).willReturn(Optional.of(activeComment));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(any(), any(), any())).willReturn(false);
        given(reportRepository.saveAndFlush(any())).willReturn(commentReport);
        given(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.COMMENT, commentId))
                .willReturn(3L);

        reportService.create(REPORTER_ID,
                new ReportCreateRequest(ReportTargetType.COMMENT, commentId, ReportReason.SPAM, null));

        assertThat(activeComment.getStatus()).isEqualTo(CommentStatus.DELETED);
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
                ReportReason.SPAM, null);
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
                COMP_POST_ID, ReportReason.SPAM, null);
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
    @DisplayName("존재하지 않는 신고 관리자 상세 조회 → REPORT_NOT_FOUND")
    void getReport_없는신고() {
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getReport(REPORT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }
}
