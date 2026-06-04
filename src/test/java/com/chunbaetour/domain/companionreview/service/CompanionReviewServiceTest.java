package com.chunbaetour.domain.companionreview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.companionreview.dto.request.CompanionReviewCreateRequest;
import com.chunbaetour.domain.companionreview.dto.response.CompanionReviewCreateResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionScoreResponse;
import com.chunbaetour.domain.companionreview.entity.CompanionReview;
import com.chunbaetour.domain.companionreview.repository.CompanionReviewRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanionReviewServiceTest {

    @InjectMocks private CompanionReviewService companionReviewService;
    @Mock private CompanionReviewRepository companionReviewRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;

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

    // 채팅방 비참여자 → CR_003
    @Test
    void createReview_notMember_throwsNotMember() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(10L, 2L, 5, null);
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(10L), eq(1L), any())).willReturn(false);

        assertThatThrownBy(() -> companionReviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_REVIEW_NOT_MEMBER));
        verify(companionReviewRepository, never()).save(any());
    }

    // target이 채팅방 비참여자 → CR_003
    @Test
    void createReview_targetNotMember_throwsNotMember() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(10L, 2L, 5, null);
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(10L), eq(1L), any())).willReturn(true);
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(10L), eq(2L), any())).willReturn(false);

        assertThatThrownBy(() -> companionReviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_REVIEW_NOT_MEMBER));
        verify(companionReviewRepository, never()).save(any());
    }

    // 중복 리뷰 → CR_001
    @Test
    void createReview_duplicate_throwsAlreadyExists() {
        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(10L, 2L, 5, null);
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(10L), eq(1L), any())).willReturn(true);
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(10L), eq(2L), any())).willReturn(true);
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
        Account target = buildAccount(2L, 0f, 0);
        CompanionReview saved = buildReview(1L, 1L, 2L, 10L, 4, "좋았어요");

        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(10L), eq(1L), any())).willReturn(true);
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                eq(10L), eq(2L), any())).willReturn(true);
        given(companionReviewRepository.existsByReviewerIdAndTargetUserIdAndChatRoomId(1L, 2L, 10L))
                .willReturn(false);
        given(accountRepository.findById(2L)).willReturn(Optional.of(target));
        given(companionReviewRepository.save(any(CompanionReview.class))).willReturn(saved);

        CompanionReviewCreateResponse response = companionReviewService.createReview(1L, request);

        assertThat(response.score()).isEqualTo(4);
        assertThat(response.writerUserId()).isEqualTo(1L);
        assertThat(target.getCompanionReviewCount()).isEqualTo(1);
        assertThat(target.getCompanionScore()).isEqualTo(4.0f);
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
        Account account = buildAccount(1L, 4.5f, 2);
        List<Object[]> raw = List.<Object[]>of(new Object[]{5, 2L});
        given(accountRepository.findById(1L)).willReturn(Optional.of(account));
        given(companionReviewRepository.countByScoreForTargetUser(1L)).willReturn(raw);

        CompanionScoreResponse response = companionReviewService.getCompanionScore(1L);

        assertThat(response.averageScore()).isEqualTo(4.5f);
        assertThat(response.reviewCount()).isEqualTo(2);
        assertThat(response.scoreDistribution().get("5")).isEqualTo(2L);
        assertThat(response.scoreDistribution().get("1")).isEqualTo(0L);
    }

    private Account buildAccount(Long id, float score, int count) {
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
