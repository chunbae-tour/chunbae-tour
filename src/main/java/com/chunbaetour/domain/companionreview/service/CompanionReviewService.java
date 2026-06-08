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
import com.chunbaetour.domain.companionreview.event.CompanionScoreCacheEvictEvent;
import com.chunbaetour.domain.companionreview.repository.CompanionReviewRepository;
import com.chunbaetour.domain.companionreview.repository.ScoreCountProjection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher applicationEventPublisher;

    // 동행 리뷰 등록 — 참여자 검증, 자기 자신 방지, 중복 방지, companionScore 증분 갱신
    // PESSIMISTIC_WRITE 락으로 동시 점수 갱신 경합 방어
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

        // PESSIMISTIC_WRITE — 동시 리뷰 등록으로 인한 이중 점수 갱신 경합 방어
        Account target = accountRepository.findByIdWithLock(request.targetUserId())
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

        // AFTER_COMMIT evict — commit 전 stale 재캐싱 최소화 (극히 드문 race는 TTL 만료 시 자가 교정)
        applicationEventPublisher.publishEvent(new CompanionScoreCacheEvictEvent(request.targetUserId()));

        return CompanionReviewCreateResponse.from(review);
    }

    // 동행 점수 조회 — averageScore, reviewCount, scoreDistribution (TTL 10분 캐싱)
    @Cacheable(value = "companionScore", key = "#userId")
    public CompanionScoreResponse getCompanionScore(Long userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<ScoreCountProjection> distribution = companionReviewRepository.countByScoreForTargetUser(userId);
        return CompanionScoreResponse.of(account, distribution);
    }
}
