package com.chunbaetour.domain.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import com.chunbaetour.domain.place.PlaceReview;
import com.chunbaetour.domain.place.PlaceReviewStatus;
import com.chunbaetour.domain.place.repository.PlaceReviewRepository;
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.ReportRepository;
import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import com.chunbaetour.domain.report.service.SanctionService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SanctionService} 콘텐츠 자동 숨김 단위 테스트 (PR5).
 * SUSPEND_7D 이상 → 신고 콘텐츠 숨김 / PERMANENT → 전체 콘텐츠 숨김.
 * (제재 단계 계산 자체는 test/ 브랜치 SanctionServiceTest에서 커버 — 여기선 숨김 동작만)
 */
@ExtendWith(MockitoExtension.class)
class SanctionContentHideTest {

    private static final long USER_ID = 10L;
    private static final long REPORT_ID = 99L;
    private static final long FREE_POST_ID = 11L;
    private static final long REVIEW_ID = 12L;

    @Spy
    Clock clock = Clock.fixed(Instant.parse("2026-06-12T12:00:00Z"), ZoneOffset.UTC);

    @Mock UserSanctionRepository userSanctionRepository;
    @Mock AccountRepository accountRepository;
    @Mock ReportRepository reportRepository;
    @Mock CompanionPostRepository companionPostRepository;
    @Mock FreePostRepository freePostRepository;
    @Mock CommentRepository commentRepository;
    @Mock PlaceReviewRepository placeReviewRepository;

    @InjectMocks
    SanctionService sanctionService;

    private void noHigherExistingSanction(ReportTargetType type) {
        given(userSanctionRepository.findByUserIdAndTargetType(USER_ID, type)).willReturn(List.of());
    }

    private void noCrossDomain() {
        given(userSanctionRepository.countActiveDistinctDomainsByUserId(eq(USER_ID), any())).willReturn(1L);
    }

    private Report reportWithTarget(long targetId) {
        Report report = org.mockito.Mockito.mock(Report.class);
        given(report.getTargetId()).willReturn(targetId);
        return report;
    }

    @Test
    void suspend7d_postFree_hides_reported_post() {
        noHigherExistingSanction(ReportTargetType.POST_FREE);
        noCrossDomain();
        Report report = reportWithTarget(FREE_POST_ID);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        FreePost post = org.mockito.Mockito.mock(FreePost.class);
        given(post.getStatus()).willReturn(FreePostStatus.ACTIVE);
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.of(post));

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.POST_FREE, 5);

        verify(post).hide();
    }

    @Test
    void warning_postFree_does_not_hide() {
        noHigherExistingSanction(ReportTargetType.POST_FREE);
        noCrossDomain();

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.POST_FREE, 3);

        // WARNING(severity 1) < SUSPEND_7D → hideReportedContent 미진입
        verify(reportRepository, never()).findById(any());
    }

    @Test
    void suspend7d_review_deletes_reported_review() {
        noHigherExistingSanction(ReportTargetType.REVIEW);
        noCrossDomain();
        Report report = reportWithTarget(REVIEW_ID);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        PlaceReview review = org.mockito.Mockito.mock(PlaceReview.class);
        given(review.getStatus()).willReturn(PlaceReviewStatus.ACTIVE);
        given(placeReviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.REVIEW, 5);

        verify(review).delete();
    }

    @Test
    void permanent_postFree_hides_all_user_content() {
        noHigherExistingSanction(ReportTargetType.POST_FREE);
        noCrossDomain();
        Report report = reportWithTarget(FREE_POST_ID);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        FreePost post = org.mockito.Mockito.mock(FreePost.class);
        given(post.getStatus()).willReturn(FreePostStatus.ACTIVE);
        given(freePostRepository.findById(FREE_POST_ID)).willReturn(Optional.of(post));

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.POST_FREE, 15);

        // PERMANENT → 전체 콘텐츠 일괄 숨김
        verify(companionPostRepository).hideAllActiveByAuthorId(USER_ID);
        verify(freePostRepository).hideAllActiveByAuthorId(USER_ID);
        verify(commentRepository).hideAllActiveByAuthorId(USER_ID);
    }

    @Test
    void suspend7d_user_does_not_hide_content() {
        noHigherExistingSanction(ReportTargetType.USER);
        noCrossDomain();
        // USER 도메인 → Account 정지 (계정 조회), 콘텐츠 숨김 경로 미진입
        given(accountRepository.findById(USER_ID)).willReturn(Optional.empty());

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.USER, 5);

        verify(reportRepository, never()).findById(any());
    }
}
