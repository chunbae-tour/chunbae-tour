package com.chunbaetour.domain.report.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
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
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.event.ReportContentActionEvent;
import com.chunbaetour.domain.report.type.ReportAction;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 콘텐츠 제재 리스너 가드 검증.
 * PR5에서 ReportService 동기 처리 → 이벤트 기반으로 이전된 가드 로직
 * (이미 DELETED 스킵, 멱등, 탈퇴 계정 정지 생략)을 리스너 단위로 검증.
 */
class ReportListenerTest {

    private static final Long REPORT_ID = 1L;
    private static final Long TARGET_ID = 10L;
    private static final Long AUTHOR_ID = 50L;

    private ReportContentActionEvent event(ReportTargetType type, ReportAction action) {
        return new ReportContentActionEvent(REPORT_ID, type, TARGET_ID, action);
    }

    // ── CompanionPostReportListener ──────────────────────────────────────────

    @Test
    @DisplayName("CompanionPost DELETE — 이미 DELETED면 hide 스킵")
    void companion_delete_alreadyDeleted_skips() {
        CompanionPostRepository repo = mock(CompanionPostRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        CompanionPost post = mock(CompanionPost.class);
        given(post.getStatus()).willReturn(CompanionPostStatus.DELETED);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(post));

        new CompanionPostReportListener(repo, accRepo)
                .handle(event(ReportTargetType.POST_COMPANION, ReportAction.DELETE));

        then(post).should(never()).hide();
    }

    @Test
    @DisplayName("CompanionPost DELETE — 활성이면 hide")
    void companion_delete_active_hides() {
        CompanionPostRepository repo = mock(CompanionPostRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        CompanionPost post = mock(CompanionPost.class);
        given(post.getStatus()).willReturn(CompanionPostStatus.ACTIVE);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(post));

        new CompanionPostReportListener(repo, accRepo)
                .handle(event(ReportTargetType.POST_COMPANION, ReportAction.DELETE));

        then(post).should().hide();
    }

    @Test
    @DisplayName("CompanionPost — targetType 불일치 시 no-op")
    void companion_wrongType_noop() {
        CompanionPostRepository repo = mock(CompanionPostRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);

        new CompanionPostReportListener(repo, accRepo)
                .handle(event(ReportTargetType.POST_FREE, ReportAction.DELETE));

        then(repo).should(never()).findById(TARGET_ID);
    }

    // ── FreePostReportListener ───────────────────────────────────────────────

    @Test
    @DisplayName("FreePost DELETE — 이미 DELETED면 hide 스킵")
    void free_delete_alreadyDeleted_skips() {
        FreePostRepository repo = mock(FreePostRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        FreePost post = mock(FreePost.class);
        given(post.getStatus()).willReturn(FreePostStatus.DELETED);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(post));

        new FreePostReportListener(repo, accRepo)
                .handle(event(ReportTargetType.POST_FREE, ReportAction.DELETE));

        then(post).should(never()).hide();
    }

    @Test
    @DisplayName("FreePost DELETE — 활성이면 hide")
    void free_delete_active_hides() {
        FreePostRepository repo = mock(FreePostRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        FreePost post = mock(FreePost.class);
        given(post.getStatus()).willReturn(FreePostStatus.ACTIVE);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(post));

        new FreePostReportListener(repo, accRepo)
                .handle(event(ReportTargetType.POST_FREE, ReportAction.DELETE));

        then(post).should().hide();
    }

    // ── CommentReportListener ────────────────────────────────────────────────

    @Test
    @DisplayName("Comment DELETE — 이미 DELETED면 delete 스킵 (멱등)")
    void comment_delete_alreadyDeleted_skips() {
        CommentRepository repo = mock(CommentRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        Comment comment = mock(Comment.class);
        given(comment.getStatus()).willReturn(CommentStatus.DELETED);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(comment));

        new CommentReportListener(repo, accRepo)
                .handle(event(ReportTargetType.COMMENT, ReportAction.DELETE));

        then(comment).should(never()).delete();
    }

    @Test
    @DisplayName("Comment DELETE — 활성이면 delete")
    void comment_delete_active_deletes() {
        CommentRepository repo = mock(CommentRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        Comment comment = mock(Comment.class);
        given(comment.getStatus()).willReturn(CommentStatus.ACTIVE);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(comment));

        new CommentReportListener(repo, accRepo)
                .handle(event(ReportTargetType.COMMENT, ReportAction.DELETE));

        then(comment).should().delete();
    }

    // ── UserReportListener ───────────────────────────────────────────────────

    @Test
    @DisplayName("User SUSPEND — 탈퇴(DELETED) 계정이면 suspend 스킵")
    void user_suspend_deleted_skips() {
        AccountRepository accRepo = mock(AccountRepository.class);
        Account acc = mock(Account.class);
        given(acc.getStatus()).willReturn(AccountStatus.DELETED);
        given(accRepo.findById(TARGET_ID)).willReturn(Optional.of(acc));

        new UserReportListener(accRepo)
                .handle(event(ReportTargetType.USER, ReportAction.SUSPEND));

        then(acc).should(never()).suspend();
    }

    @Test
    @DisplayName("User SUSPEND — 이미 SUSPENDED면 REPORT_TARGET_ALREADY_SUSPENDED (#487)")
    void user_suspend_alreadySuspended_throws() {
        AccountRepository accRepo = mock(AccountRepository.class);
        Account acc = mock(Account.class);
        given(acc.getStatus()).willReturn(AccountStatus.SUSPENDED);
        given(accRepo.findById(TARGET_ID)).willReturn(Optional.of(acc));

        UserReportListener listener = new UserReportListener(accRepo);
        ReportContentActionEvent evt = event(ReportTargetType.USER, ReportAction.SUSPEND);

        assertThatThrownBy(() -> listener.handle(evt))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_TARGET_ALREADY_SUSPENDED);
        then(acc).should(never()).suspend();
    }

    @Test
    @DisplayName("User SUSPEND — 활성 계정이면 suspend")
    void user_suspend_active_suspends() {
        AccountRepository accRepo = mock(AccountRepository.class);
        Account acc = mock(Account.class);
        given(acc.getStatus()).willReturn(AccountStatus.ACTIVE);
        given(accRepo.findById(TARGET_ID)).willReturn(Optional.of(acc));

        new UserReportListener(accRepo)
                .handle(event(ReportTargetType.USER, ReportAction.SUSPEND));

        then(acc).should().suspend();
    }

    // ── ReviewReportListener ─────────────────────────────────────────────────

    @Test
    @DisplayName("Review DELETE — 이미 DELETED면 delete 스킵")
    void review_delete_alreadyDeleted_skips() {
        PlaceReviewRepository repo = mock(PlaceReviewRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        PlaceReview review = mock(PlaceReview.class);
        given(review.getStatus()).willReturn(PlaceReviewStatus.DELETED);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(review));

        new ReviewReportListener(repo, accRepo)
                .handle(event(ReportTargetType.REVIEW, ReportAction.DELETE));

        then(review).should(never()).delete();
    }

    @Test
    @DisplayName("Review DELETE — 활성이면 delete")
    void review_delete_active_deletes() {
        PlaceReviewRepository repo = mock(PlaceReviewRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        PlaceReview review = mock(PlaceReview.class);
        given(review.getStatus()).willReturn(PlaceReviewStatus.ACTIVE);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(review));

        new ReviewReportListener(repo, accRepo)
                .handle(event(ReportTargetType.REVIEW, ReportAction.DELETE));

        then(review).should().delete();
    }

    @Test
    @DisplayName("Review SUSPEND — 작성자 활성 계정이면 suspend")
    void review_suspend_active_suspends() {
        PlaceReviewRepository repo = mock(PlaceReviewRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        PlaceReview review = mock(PlaceReview.class);
        Account author = mock(Account.class);
        given(author.getId()).willReturn(AUTHOR_ID);
        given(review.getAuthor()).willReturn(author);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(review));
        Account target = mock(Account.class);
        given(target.getStatus()).willReturn(AccountStatus.ACTIVE);
        given(accRepo.findById(AUTHOR_ID)).willReturn(Optional.of(target));

        new ReviewReportListener(repo, accRepo)
                .handle(event(ReportTargetType.REVIEW, ReportAction.SUSPEND));

        then(target).should().suspend();
    }

    @Test
    @DisplayName("Review SUSPEND — 작성자 이미 SUSPENDED면 REPORT_TARGET_ALREADY_SUSPENDED (#487)")
    void review_suspend_alreadySuspended_throws() {
        PlaceReviewRepository repo = mock(PlaceReviewRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        PlaceReview review = mock(PlaceReview.class);
        Account author = mock(Account.class);
        given(author.getId()).willReturn(AUTHOR_ID);
        given(review.getAuthor()).willReturn(author);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(review));
        Account target = mock(Account.class);
        given(target.getStatus()).willReturn(AccountStatus.SUSPENDED);
        given(accRepo.findById(AUTHOR_ID)).willReturn(Optional.of(target));

        ReviewReportListener listener = new ReviewReportListener(repo, accRepo);
        ReportContentActionEvent evt = event(ReportTargetType.REVIEW, ReportAction.SUSPEND);

        assertThatThrownBy(() -> listener.handle(evt))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPORT_TARGET_ALREADY_SUSPENDED);
        then(target).should(never()).suspend();
    }

    // ── RESTORE 액션 (PR5 — 관리자 콘텐츠 숨김 해제) ──────────────────────────

    @Test
    @DisplayName("CompanionPost RESTORE — restore 호출")
    void companion_restore_calls_restore() {
        CompanionPostRepository repo = mock(CompanionPostRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        CompanionPost post = mock(CompanionPost.class);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(post));

        new CompanionPostReportListener(repo, accRepo)
                .handle(event(ReportTargetType.POST_COMPANION, ReportAction.RESTORE));

        then(post).should().restore();
    }

    @Test
    @DisplayName("FreePost RESTORE — restore 호출")
    void free_restore_calls_restore() {
        FreePostRepository repo = mock(FreePostRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        FreePost post = mock(FreePost.class);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(post));

        new FreePostReportListener(repo, accRepo)
                .handle(event(ReportTargetType.POST_FREE, ReportAction.RESTORE));

        then(post).should().restore();
    }

    @Test
    @DisplayName("Comment RESTORE — restore 호출")
    void comment_restore_calls_restore() {
        CommentRepository repo = mock(CommentRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        Comment comment = mock(Comment.class);
        given(repo.findById(TARGET_ID)).willReturn(Optional.of(comment));

        new CommentReportListener(repo, accRepo)
                .handle(event(ReportTargetType.COMMENT, ReportAction.RESTORE));

        then(comment).should().restore();
    }
}
