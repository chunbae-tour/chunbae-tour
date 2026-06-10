package com.chunbaetour.domain.companionreview.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.companionreview.dto.request.CompanionReviewCreateRequest;
import com.chunbaetour.domain.companionreview.dto.response.CompanionReviewCreateResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionReviewResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionScoreResponse;
import com.chunbaetour.domain.companionreview.entity.Companion;
import com.chunbaetour.domain.companionreview.entity.CompanionReview;
import com.chunbaetour.domain.companionreview.event.CompanionScoreCacheEvictEvent;
import com.chunbaetour.domain.companionreview.repository.CompanionParticipantRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionReviewRepository;
import com.chunbaetour.domain.companionreview.repository.ScoreCountProjection;
import com.chunbaetour.domain.companionreview.type.CompanionStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanionReviewService {

    private final CompanionReviewRepository companionReviewRepository;
    private final AccountRepository accountRepository;
    private final CompanionRepository companionRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    // 동행 리뷰 등록 — 동행 참여자 검증, 자기 자신 방지, 중복 방지, companionScore 증분 갱신
    // PESSIMISTIC_WRITE 락으로 동시 점수 갱신 경합 방어
    @Transactional
    public CompanionReviewCreateResponse createReview(Long reviewerId, CompanionReviewCreateRequest request) {
        if (reviewerId.equals(request.targetUserId())) {
            throw new BusinessException(ErrorCode.COMPANION_REVIEW_SELF_NOT_ALLOWED);
        }

        // 동행 없는 chatRoomId도 비참여자에게는 NOT_MEMBER로 통일 — 동행 존재 여부 추론 방지
        Companion companion = companionRepository.findByChatRoomId(request.chatRoomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_REVIEW_NOT_MEMBER));

        // reviewer/target 둘 다 참여자인지 IN절 단일 쿼리로 검증
        long participantCount = companionParticipantRepository.countByCompanionIdAndUserIdIn(
                companion.getId(), List.of(reviewerId, request.targetUserId()));
        if (participantCount != 2) {
            throw new BusinessException(ErrorCode.COMPANION_REVIEW_NOT_MEMBER);
        }

        // 동행 ENDED 후에만 리뷰 작성 가능
        if (companion.getStatus() != CompanionStatus.ENDED) {
            throw new BusinessException(ErrorCode.COMPANION_NOT_ENDED);
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

    // 동행 리뷰 목록 조회 — id DESC 커서 페이징, N+1 방지: reviewerId 일괄 조회 후 Account Map
    public CursorPageResponse<CompanionReviewResponse> getReviews(Long targetUserId, String cursor, int size) {
        if (!accountRepository.existsById(targetUserId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Long cursorId = CursorUtils.decodeSafe(cursor);

        List<CompanionReview> reviews = companionReviewRepository.findByTargetUserIdWithCursor(
                targetUserId, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = reviews.size() > size;
        List<CompanionReview> page = hasNext ? reviews.subList(0, size) : reviews;

        String nextCursor = hasNext ? CursorUtils.encode(page.get(page.size() - 1).getId()) : null;

        List<Long> reviewerIds = page.stream()
                .map(CompanionReview::getReviewerId)
                .distinct()
                .toList();
        Map<Long, Account> accountMap = accountRepository.findAllById(reviewerIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        return new CursorPageResponse<>(
                page.stream()
                        .map(r -> CompanionReviewResponse.of(r, accountMap.get(r.getReviewerId())))
                        .toList(),
                nextCursor,
                hasNext,
                size
        );
    }
}
