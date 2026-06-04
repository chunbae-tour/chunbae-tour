package com.chunbaetour.domain.companionreview.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.companionreview.dto.request.CompanionReviewCreateRequest;
import com.chunbaetour.domain.companionreview.dto.response.CompanionReviewCreateResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionScoreResponse;
import com.chunbaetour.domain.companionreview.entity.CompanionReview;
import com.chunbaetour.domain.companionreview.repository.CompanionReviewRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanionReviewService {

    private final CompanionReviewRepository companionReviewRepository;
    private final AccountRepository accountRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    // 동행 리뷰 등록 — 참여자 검증, 자기 자신 방지, 중복 방지, companionScore 증분 갱신
    // 등록 후 target 캐시 삭제 — commit 전 동시 조회 시 TTL까지 stale 가능 (저영향)
    @CacheEvict(value = "companionScore", key = "#request.targetUserId()")
    @Transactional
    public CompanionReviewCreateResponse createReview(Long reviewerId, CompanionReviewCreateRequest request) {
        if (reviewerId.equals(request.targetUserId())) {
            throw new BusinessException(ErrorCode.COMPANION_REVIEW_SELF_NOT_ALLOWED);
        }

        List<ChatMemberState> activeStates = List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                request.chatRoomId(), reviewerId, activeStates)) {
            throw new BusinessException(ErrorCode.COMPANION_REVIEW_NOT_MEMBER);
        }
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                request.chatRoomId(), request.targetUserId(), activeStates)) {
            throw new BusinessException(ErrorCode.COMPANION_REVIEW_NOT_MEMBER);
        }

        if (companionReviewRepository.existsByReviewerIdAndTargetUserIdAndChatRoomId(
                reviewerId, request.targetUserId(), request.chatRoomId())) {
            throw new BusinessException(ErrorCode.COMPANION_REVIEW_ALREADY_EXISTS);
        }

        Account target = accountRepository.findById(request.targetUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        CompanionReview review;
        try {
            review = companionReviewRepository.save(CompanionReview.builder()
                    .reviewerId(reviewerId)
                    .targetUserId(request.targetUserId())
                    .chatRoomId(request.chatRoomId())
                    .score(request.score())
                    .content(request.content())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // uq_companion_review 위반 — 동시 요청으로 앱 레벨 체크를 통과한 경우
            throw new BusinessException(ErrorCode.COMPANION_REVIEW_ALREADY_EXISTS);
        }

        target.addCompanionReview(request.score());

        return CompanionReviewCreateResponse.from(review);
    }

    // 동행 점수 조회 — averageScore, reviewCount, scoreDistribution (TTL 10분 캐싱)
    @Cacheable(value = "companionScore", key = "#userId")
    public CompanionScoreResponse getCompanionScore(Long userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Object[]> distribution = companionReviewRepository.countByScoreForTargetUser(userId);
        return CompanionScoreResponse.of(account, distribution);
    }
}
