package com.chunbaetour.domain.companion.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.companion.review.dto.request.CompanionReviewCreateRequest;
import com.chunbaetour.domain.companion.review.dto.response.CompanionReviewCreateResponse;
import com.chunbaetour.domain.companion.review.dto.response.CompanionReviewResponse;
import com.chunbaetour.domain.companion.review.dto.response.CompanionScoreResponse;
import com.chunbaetour.domain.companion.entity.Companion;
import com.chunbaetour.domain.companion.review.entity.CompanionReview;
import com.chunbaetour.domain.companion.repository.CompanionParticipantRepository;
import com.chunbaetour.domain.companion.repository.CompanionRepository;
import com.chunbaetour.domain.companion.review.repository.CompanionReviewRepository;
import com.chunbaetour.domain.companion.review.repository.ScoreCountProjection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CompanionReviewServiceTest {

    @InjectMocks private CompanionReviewService companionReviewService;
    @Mock private CompanionReviewRepository companionReviewRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CompanionRepository companionRepository;
    @Mock private CompanionParticipantRepository companionParticipantRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    // ===== createReview =====

    // 자기 자신에게 리뷰 시 CR_002
    @Test
    void createReview_selfReview_throwsSelfNotAllowed() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(1L, 1L, 5, null);

        assertThatThrownBy(() -> companionReviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_REVIEW_SELF_NOT_ALLOWED));
        verify(companionReviewRepository, never()).save(any());
    }

    // 동행 없는 채팅방 → CR_003 (동행 존재 여부 추론 방지를 위해 NOT_MEMBER로 통일)
    @Test
    void createReview_noCompanion_throwsNotMember() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(10L, 2L, 5, null);
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companionReviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_REVIEW_NOT_MEMBER));
        verify(companionReviewRepository, never()).save(any());
    }

    // 동행 참여자 아닌 reviewer → CR_003
    @Test
    void createReview_reviewerNotParticipant_throwsNotMember() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(10L, 2L, 5, null);
        Companion companion = buildCompanion(100L, 10L);
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(companion));
        given(companionParticipantRepository.countByCompanionIdAndUserIdIn(100L, List.of(1L, 2L))).willReturn(1L);

        assertThatThrownBy(() -> companionReviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_REVIEW_NOT_MEMBER));
        verify(companionReviewRepository, never()).save(any());
    }

    // 동행 참여자 아닌 target → CR_003
    @Test
    void createReview_targetNotParticipant_throwsNotMember() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(10L, 2L, 5, null);
        Companion companion = buildCompanion(100L, 10L);
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(companion));
        given(companionParticipantRepository.countByCompanionIdAndUserIdIn(100L, List.of(1L, 2L))).willReturn(1L);

        assertThatThrownBy(() -> companionReviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_REVIEW_NOT_MEMBER));
        verify(companionReviewRepository, never()).save(any());
    }

    // 동행 ONGOING 상태 → CR_009
    @Test
    void createReview_companionNotEnded_throwsNotEnded() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(10L, 2L, 5, null);
        Companion companion = buildCompanion(100L, 10L);
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(companion));
        given(companionParticipantRepository.countByCompanionIdAndUserIdIn(100L, List.of(1L, 2L))).willReturn(2L);

        assertThatThrownBy(() -> companionReviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_NOT_ENDED));
        verify(companionReviewRepository, never()).save(any());
    }

    // 중복 리뷰 → CR_001
    @Test
    void createReview_duplicate_throwsAlreadyExists() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(10L, 2L, 5, null);
        Companion companion = buildCompanion(100L, 10L);
        companion.end();
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(companion));
        given(companionParticipantRepository.countByCompanionIdAndUserIdIn(100L, List.of(1L, 2L))).willReturn(2L);
        given(companionParticipantRepository.countByCompanionIdAndUserIdInAndEndedAtIsNotNull(100L, List.of(1L, 2L)))
                .willReturn(2L);
        given(companionReviewRepository.existsByReviewerIdAndTargetUserIdAndChatRoomId(1L, 2L, 10L))
                .willReturn(true);

        assertThatThrownBy(() -> companionReviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_REVIEW_ALREADY_EXISTS));
        verify(companionReviewRepository, never()).save(any());
    }

    // 정상 등록 → 201 + companionScore 갱신
    @Test
    void createReview_success_savesAndUpdatesScore() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(10L, 2L, 4, "좋았어요");
        Companion companion = buildCompanion(100L, 10L);
        companion.end();
        Account target = buildAccount(2L, 0.0, 0);
        CompanionReview saved = buildReview(1L, 1L, 2L, 10L, 4, "좋았어요");

        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(companion));
        given(companionParticipantRepository.countByCompanionIdAndUserIdIn(100L, List.of(1L, 2L))).willReturn(2L);
        given(companionParticipantRepository.countByCompanionIdAndUserIdInAndEndedAtIsNotNull(100L, List.of(1L, 2L)))
                .willReturn(2L);
        given(companionReviewRepository.existsByReviewerIdAndTargetUserIdAndChatRoomId(1L, 2L, 10L))
                .willReturn(false);
        given(accountRepository.findByIdWithLock(2L)).willReturn(Optional.of(target));
        given(accountRepository.findByIdWithLock(1L)).willReturn(Optional.of(buildAccount(1L, 0.0, 0)));
        given(companionReviewRepository.save(any(CompanionReview.class))).willReturn(saved);

        CompanionReviewCreateResponse response = companionReviewService.createReview(1L, request);

        assertThat(response.score()).isEqualTo(4);
        assertThat(response.writerUserId()).isEqualTo(1L);
        assertThat(target.getCompanionReviewCount()).isEqualTo(1);
        assertThat(target.getCompanionScore()).isEqualTo(4.0);
    }

    // 한쪽만 endParticipation 완료 → CR_011 (고도화 #2)
    @Test
    void createReview_participantsNotBothEnded_throwsParticipantNotEnded() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(10L, 2L, 5, null);
        Companion companion = buildCompanion(100L, 10L);
        companion.end();
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(companion));
        given(companionParticipantRepository.countByCompanionIdAndUserIdIn(100L, List.of(1L, 2L))).willReturn(2L);
        given(companionParticipantRepository.countByCompanionIdAndUserIdInAndEndedAtIsNotNull(100L, List.of(1L, 2L)))
                .willReturn(1L);

        assertThatThrownBy(() -> companionReviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_REVIEW_PARTICIPANT_NOT_ENDED));
        verify(companionReviewRepository, never()).save(any());
    }

    // target 계정이 정지 상태 → CR_012 (고도화 #2)
    @Test
    void createReview_targetSuspended_throwsSuspendedAccount() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(10L, 2L, 5, null);
        Companion companion = buildCompanion(100L, 10L);
        companion.end();
        Account target = buildAccount(2L, 0.0, 0);
        target.suspend();
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(companion));
        given(companionParticipantRepository.countByCompanionIdAndUserIdIn(100L, List.of(1L, 2L))).willReturn(2L);
        given(companionParticipantRepository.countByCompanionIdAndUserIdInAndEndedAtIsNotNull(100L, List.of(1L, 2L)))
                .willReturn(2L);
        given(companionReviewRepository.existsByReviewerIdAndTargetUserIdAndChatRoomId(1L, 2L, 10L))
                .willReturn(false);
        given(accountRepository.findByIdWithLock(2L)).willReturn(Optional.of(target));
        given(accountRepository.findByIdWithLock(1L)).willReturn(Optional.of(buildAccount(1L, 0.0, 0)));

        assertThatThrownBy(() -> companionReviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_REVIEW_SUSPENDED_ACCOUNT));
        verify(companionReviewRepository, never()).save(any());
    }

    // reviewer 계정이 정지 상태 → CR_012 (고도화 #2)
    @Test
    void createReview_reviewerSuspended_throwsSuspendedAccount() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(10L, 2L, 5, null);
        Companion companion = buildCompanion(100L, 10L);
        companion.end();
        Account target = buildAccount(2L, 0.0, 0);
        Account reviewer = buildAccount(1L, 0.0, 0);
        reviewer.suspend();
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(companion));
        given(companionParticipantRepository.countByCompanionIdAndUserIdIn(100L, List.of(1L, 2L))).willReturn(2L);
        given(companionParticipantRepository.countByCompanionIdAndUserIdInAndEndedAtIsNotNull(100L, List.of(1L, 2L)))
                .willReturn(2L);
        given(companionReviewRepository.existsByReviewerIdAndTargetUserIdAndChatRoomId(1L, 2L, 10L))
                .willReturn(false);
        given(accountRepository.findByIdWithLock(2L)).willReturn(Optional.of(target));
        given(accountRepository.findByIdWithLock(1L)).willReturn(Optional.of(reviewer));

        assertThatThrownBy(() -> companionReviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_REVIEW_SUSPENDED_ACCOUNT));
        verify(companionReviewRepository, never()).save(any());
    }

    // ===== getCompanionScore =====

    // 존재하지 않는 유저 → USER_NOT_FOUND
    @Test
    void getCompanionScore_userNotFound_throwsNotFound() {
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companionReviewService.getCompanionScore(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    // 정상 조회 — scoreDistribution 빈 점수는 0
    @Test
    void getCompanionScore_success_returnsDistributionWithZeros() {
        Account account = buildAccount(1L, 4.5, 2);
        ScoreCountProjection proj = new ScoreCountProjection() {
            public Integer getScore() { return 5; }
            public Long getCount() { return 2L; }
        };
        given(accountRepository.findById(1L)).willReturn(Optional.of(account));
        given(companionReviewRepository.countByScoreForTargetUser(1L)).willReturn(List.of(proj));

        CompanionScoreResponse response = companionReviewService.getCompanionScore(1L);

        assertThat(response.averageScore()).isEqualTo(4.5);
        assertThat(response.reviewCount()).isEqualTo(2);
        assertThat(response.scoreDistribution().get("5")).isEqualTo(2L);
        assertThat(response.scoreDistribution().get("1")).isEqualTo(0L);
    }

    // ===== getReviews =====

    // 존재하지 않는 유저 → USER_NOT_FOUND
    @Test
    void getReviews_userNotFound_throwsNotFound() {
        given(accountRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> companionReviewService.getReviews(999L, null, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    // 정상 조회 — reviewerNickname 포함, hasNext=false
    @Test
    void getReviews_success_returnsReviews() {
        Long targetUserId = 1L;
        CompanionReview review = buildReview(10L, 2L, targetUserId, 100L, 5, "좋았어요");
        Account reviewer = buildAccount(2L, 0.0, 0);

        given(accountRepository.existsById(targetUserId)).willReturn(true);
        given(companionReviewRepository.findByTargetUserIdWithCursor(eq(targetUserId), isNull(), eq(PageRequest.of(0, 11))))
                .willReturn(List.of(review));
        given(accountRepository.findAllById(List.of(2L))).willReturn(List.of(reviewer));

        CursorPageResponse<CompanionReviewResponse> response = companionReviewService.getReviews(targetUserId, null, 10);

        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).reviewId()).isEqualTo(10L);
        assertThat(response.content().get(0).reviewerId()).isEqualTo(2L);
        assertThat(response.content().get(0).reviewerNickname()).isEqualTo("nick");
        assertThat(response.content().get(0).reviewerProfileImageUrl()).isEqualTo("https://example.com/profile.png");
    }

    // size+1건 조회 시 마지막 1건 제외, hasNext=true + nextCursor 인코딩
    @Test
    void getReviews_hasNext_returnsNextCursor() {
        Long targetUserId = 1L;
        CompanionReview r1 = buildReview(10L, 2L, targetUserId, 100L, 5, "a");
        CompanionReview r2 = buildReview(9L, 2L, targetUserId, 100L, 4, "b");

        given(accountRepository.existsById(targetUserId)).willReturn(true);
        given(companionReviewRepository.findByTargetUserIdWithCursor(eq(targetUserId), isNull(), eq(PageRequest.of(0, 2))))
                .willReturn(List.of(r1, r2));
        given(accountRepository.findAllById(any())).willReturn(List.of(buildAccount(2L, 0.0, 0)));

        CursorPageResponse<CompanionReviewResponse> response = companionReviewService.getReviews(targetUserId, null, 1);

        assertThat(response.hasNext()).isTrue();
        assertThat(response.content()).hasSize(1);
        assertThat(response.nextCursor()).isEqualTo(CursorUtils.encode(10L));
    }

    // 탈퇴한 리뷰어 → "탈퇴한 사용자" fallback
    @Test
    void getReviews_reviewerWithdrawn_fallbackNickname() {
        Long targetUserId = 1L;
        CompanionReview review = buildReview(10L, 999L, targetUserId, 100L, 5, "good");

        given(accountRepository.existsById(targetUserId)).willReturn(true);
        given(companionReviewRepository.findByTargetUserIdWithCursor(eq(targetUserId), isNull(), eq(PageRequest.of(0, 11))))
                .willReturn(List.of(review));
        given(accountRepository.findAllById(List.of(999L))).willReturn(List.of());

        CursorPageResponse<CompanionReviewResponse> response = companionReviewService.getReviews(targetUserId, null, 10);

        assertThat(response.content().get(0).reviewerNickname()).isEqualTo("탈퇴한 사용자");
        assertThat(response.content().get(0).reviewerId()).isNull();
        assertThat(response.content().get(0).reviewerProfileImageUrl()).isNull();
    }

    // 잘못된 cursor → INVALID_CURSOR
    @Test
    void getReviews_invalidCursor_throwsInvalidCursor() {
        given(accountRepository.existsById(1L)).willReturn(true);

        assertThatThrownBy(() -> companionReviewService.getReviews(1L, "!!!invalid!!!", 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CURSOR));
    }

    private Companion buildCompanion(Long id, Long chatRoomId) {
        Companion companion = Companion.builder().chatRoomId(chatRoomId)
                .tripStartDate(LocalDate.of(2026, 7, 1)).tripEndDate(LocalDate.of(2026, 7, 5)).build();
        try {
            var idField = Companion.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(companion, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return companion;
    }

    private Account buildAccount(Long id, double score, int count) {
        Account account = Account.builder()
                .email("test@test.com").password("pw").nickname("nick")
                .build();
        try {
            var idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, id);
            var profileImageField = Account.class.getDeclaredField("profileImageUrl");
            profileImageField.setAccessible(true);
            profileImageField.set(account, "https://example.com/profile.png");
            var scoreField = Account.class.getDeclaredField("companionScore");
            scoreField.setAccessible(true);
            scoreField.set(account, score);
            var countField = Account.class.getDeclaredField("companionReviewCount");
            countField.setAccessible(true);
            countField.set(account, count);
        } catch (Exception e) { throw new RuntimeException(e); }
        return account;
    }

    private CompanionReview buildReview(Long id, Long reviewerId, Long targetUserId, Long chatRoomId, int score, String content) {
        CompanionReview review = CompanionReview.builder()
                .reviewerId(reviewerId).targetUserId(targetUserId)
                .chatRoomId(chatRoomId).score(score).content(content)
                .build();
        try {
            var idField = CompanionReview.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(review, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return review;
    }
}
