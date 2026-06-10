package com.chunbaetour.domain.companionreview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.companionreview.dto.request.CompanionReviewCreateRequest;
import com.chunbaetour.domain.companionreview.dto.response.CompanionReviewCreateResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionScoreResponse;
import com.chunbaetour.domain.companionreview.entity.Companion;
import com.chunbaetour.domain.companionreview.entity.CompanionReview;
import com.chunbaetour.domain.companionreview.repository.CompanionParticipantRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionReviewRepository;
import com.chunbaetour.domain.companionreview.repository.ScoreCountProjection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
        given(companionReviewRepository.existsByReviewerIdAndTargetUserIdAndChatRoomId(1L, 2L, 10L))
                .willReturn(false);
        given(accountRepository.findByIdWithLock(2L)).willReturn(Optional.of(target));
        given(companionReviewRepository.save(any(CompanionReview.class))).willReturn(saved);

        CompanionReviewCreateResponse response = companionReviewService.createReview(1L, request);

        assertThat(response.score()).isEqualTo(4);
        assertThat(response.writerUserId()).isEqualTo(1L);
        assertThat(target.getCompanionReviewCount()).isEqualTo(1);
        assertThat(target.getCompanionScore()).isEqualTo(4.0);
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

    private Companion buildCompanion(Long id, Long chatRoomId) {
        Companion companion = Companion.builder().chatRoomId(chatRoomId).build();
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
