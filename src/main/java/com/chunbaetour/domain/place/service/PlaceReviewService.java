package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.PlaceReview;
import com.chunbaetour.domain.place.PlaceReviewStatus;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.dto.request.ReviewCreateRequest;
import com.chunbaetour.domain.place.dto.response.PlaceReviewResponse;
import com.chunbaetour.domain.place.dto.response.UserReviewResponse;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.repository.PlaceReviewRepository;
import com.chunbaetour.domain.place.type.PlaceStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 관광지 리뷰 서비스.
 *
 * <ul>
 *   <li>리뷰 작성: 1인 1리뷰 정책 검증 → 리뷰 저장 → Place 평점/리뷰수 재계산 → 캐시 무효화</li>
 *   <li>리뷰 목록 조회: 특정 관광지의 ACTIVE 리뷰 페이징 조회</li>
 * </ul>
 *
 * <p>캐시 무효화는 DB 트랜잭션 커밋 이후({@code afterCommit})에 실행된다.
 * DB 롤백 시 Redis 상태가 어긋나지 않도록 방어.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceReviewService {

    private final PlaceReviewRepository placeReviewRepository;
    private final PlaceRepository placeRepository;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 관광지 리뷰 작성.
     *
     * <p>1인 1리뷰 정책 위반 시 {@code PLACE_012 (REVIEW_ALREADY_EXISTS)} 반환.
     * 리뷰 저장 후 Place 엔티티의 평점과 리뷰 수를 즉시 재계산.
     * DB 커밋 이후 관광지 상세 캐시를 무효화하여 stale 데이터 방지.
     *
     * @param userId   작성자 ID (SecurityContext에서 추출된 본인 ID)
     * @param placeId  대상 관광지 ID
     * @param request  리뷰 내용 (별점, 내용, 이미지 목록)
     * @return 생성된 리뷰 응답 DTO
     */
    @Transactional
    public PlaceReviewResponse createReview(Long userId, Long placeId, ReviewCreateRequest request) {
        // 1. 관광지 존재 여부 및 활성 상태(ACTIVE) 확인 + 비관적 락 획득 (동시성 제어)
        Place place = placeRepository.findByIdAndStatusForUpdate(placeId, PlaceStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        // 2. 1인 1리뷰 정책 검증
        boolean alreadyReviewed = placeReviewRepository.existsByAuthorIdAndPlaceIdAndStatus(
                userId, placeId, PlaceReviewStatus.ACTIVE
        );
        if (alreadyReviewed) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        // 3. Account 엔티티 조회
        //    응답 DTO 생성 시 작성자 닉네임/프로필이 필요하므로 프록시 대신 실제 객체 로딩.
        Account author = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. 이미지 JSON 직렬화 (최대 5개는 DTO @Size 검증으로 이미 처리됨)
        String imageUrlsJson = serializeImageUrls(request.imageUrls());

        // 5. 리뷰 엔티티 생성 및 저장 (DB 유니크 제약 + 비관적 락으로 이중 방어)
        PlaceReview review = PlaceReview.create(place, author, request.rating(), request.content(), imageUrlsJson);
        try {
            placeReviewRepository.save(review);
        } catch (DataIntegrityViolationException e) {
            log.warn("리뷰 작성 중복(UNIQUE 제약) 발생. placeId={}, userId={}", placeId, userId, e);
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        // 6. Place 평점 및 리뷰 수 재계산
        recalculatePlaceRating(place);

        // 7. 관광지 상세 캐시 무효화 — DB 커밋 이후 실행
        //    rating/reviewCount가 바뀌었으므로 기존 캐시(10분 TTL)를 즉시 제거.
        registerAfterCommit(() -> evictDetailCache(placeId));

        log.debug("리뷰 작성 완료. placeId={}, userId={}, reviewId={}", placeId, userId, review.getId());

        return PlaceReviewResponse.from(review, parseImageUrls(review.getImageUrls(), review.getId()));
    }

    /**
     * 특정 관광지의 리뷰 목록 페이징 조회 (최신순).
     *
     * @param placeId  관광지 ID
     * @param pageable 페이징 정보
     * @return 리뷰 목록 (Page)
     */
    @Transactional(readOnly = true)
    public Page<PlaceReviewResponse> getPlaceReviews(Long placeId, Pageable pageable) {
        // 관광지 존재 여부 및 활성 상태(ACTIVE) 확인 (숨김/삭제 관광지 차단)
        if (placeRepository.findByIdAndStatus(placeId, PlaceStatus.ACTIVE).isEmpty()) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
        return placeReviewRepository.findActiveByPlaceId(placeId, pageable)
                .map(r -> PlaceReviewResponse.from(r, parseImageUrls(r.getImageUrls(), r.getId())));
    }

    /**
     * 내 리뷰 목록 페이징 조회 (마이페이지용).
     *
     * @param userId   작성자(본인) ID
     * @param pageable 페이징 정보
     * @return 리뷰 목록 (Page)
     */
    @Transactional(readOnly = true)
    public Page<UserReviewResponse> getUserReviews(Long userId, Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return placeReviewRepository.findActiveByAuthorId(userId, pageable)
                .map(r -> UserReviewResponse.from(r, parseImageUrls(r.getImageUrls(), r.getId())));
    }

    // ── private 헬퍼 ────────────────────────────────────────────────────

    /**
     * Place의 평점(rating)과 리뷰 수(reviewCount)를 DB의 ACTIVE 리뷰 기준으로 재계산.
     * JPQL 집계 쿼리로 SUM(rating), COUNT를 한 번에 가져와 Place 도메인 메서드 호출.
     */
    private void recalculatePlaceRating(Place place) {
        Object[] result = placeReviewRepository.sumRatingAndCount(place.getId());
        double totalScore = result[0] != null ? ((Number) result[0]).doubleValue() : 0.0;
        int reviewCount   = result[1] != null ? ((Number) result[1]).intValue()    : 0;
        place.recalculateRating(totalScore, reviewCount);
    }

    /**
     * 이미지 URL JSON 문자열을 List<String>으로 파싱.
     * Jackson 설정(날짜 포맷 등)을 스프링 컨텍스트 Bean에서 적용받아 일관성 유지.
     * 파싱 실패 시 빈 리스트 반환 (warn 로그 기록).
     */
    private List<String> parseImageUrls(String imageUrlsJson, Long reviewId) {
        if (imageUrlsJson == null || imageUrlsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imageUrlsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("리뷰 이미지 URL 파싱 실패. reviewId={}", reviewId, e);
            return List.of();
        }
    }

    /**
     * 이미지 URL 목록을 JSON 문자열로 직렬화.
     * null이거나 비어있으면 null 반환 (DB 저장 시 NULL).
     * blank 문자열 필터링 포함.
     */
    private String serializeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }
        List<String> filtered = imageUrls.stream()
                .filter(StringUtils::hasText)
                .toList();
        if (filtered.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(filtered);
        } catch (Exception e) {
            log.warn("이미지 URL 직렬화 실패. imageUrlsSize={}", imageUrls.size(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 관광지 상세 캐시 무효화.
     * rating/reviewCount 변동 후 기존 캐시가 stale 데이터를 응답하지 않도록 즉시 삭제.
     * 삭제 실패 시 자연 TTL 만료를 대기 (비즈니스 로직 차단 없이 warn 로그만 남김).
     */
    private void evictDetailCache(Long placeId) {
        try {
            stringRedisTemplate.delete(PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + placeId);
            log.debug("Place detail cache evicted after review create. placeId={}", placeId);
        } catch (Exception e) {
            log.warn("Place detail cache eviction failed after review create. placeId={}", placeId, e);
        }
    }

    /**
     * DB 트랜잭션 커밋 성공 후 {@code action}을 실행하도록 등록.
     * 롤백 시 Redis 상태가 어긋나지 않도록 afterCommit으로 분리.
     * 활성 트랜잭션이 없는 경우(테스트 등)에는 즉시 실행.
     */
    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
