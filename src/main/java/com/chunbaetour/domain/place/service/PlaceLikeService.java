package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.UserLike;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.repository.UserLikeRepository;
import com.chunbaetour.domain.place.type.PlaceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 관광지 찜하기 / 찜 취소 서비스
 *
 * <p>정책:
 * <ul>
 *   <li>찜 추가: user_likes INSERT + Redis INCR {@code place:like:{placeId}}</li>
 *   <li>찜 취소: user_likes DELETE + Redis DECR {@code place:like:{placeId}} (0 미만 방지)</li>
 *   <li>중복 찜 → PLACE_010 (LIKE_ALREADY_EXISTS)</li>
 *   <li>찜 미존재 취소 → PLACE_011 (LIKE_NOT_FOUND)</li>
 * </ul>
 *
 * <p>Redis 카운터/캐시 무효화는 DB 트랜잭션 커밋 이후({@code afterCommit})에 실행된다.
 * DB 롤백 시 Redis 상태가 어긋나는 것을 방지하기 위한 설계.
 * DB의 places.like_count 컬럼과의 정합성은 배치 동기화(PHASE 5)에서 처리할 예정.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceLikeService {

    private static final String PLACE_LIKE_COUNT_KEY  = "place:like:";
    /** 찜하기/취소 후 상세 캐시를 즉시 무효화해 stale likeCount 노출 방지 */
    private static final String PLACE_DETAIL_CACHE_KEY = "place:";

    private final UserLikeRepository userLikeRepository;
    private final PlaceRepository placeRepository;
    private final AccountRepository accountRepository;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 관광지 찜 추가
     * POST /api/v1/places/{placeId}/like
     *
     * <p>동시성 처리:
     * <ul>
     *   <li>exists 체크: 빠른 경로(fast path) — 대부분의 중복 찜을 DB 조회 전에 차단</li>
     *   <li>DataIntegrityViolationException catch: 경쟁 상태(race condition)에서 UNIQUE 제약
     *       위반이 500으로 새어나가지 않도록 LIKE_ALREADY_EXISTS로 변환하는 안전망</li>
     * </ul>
     *
     * @param userId  인증된 사용자 ID (@AuthenticationPrincipal Long userId)
     * @param placeId 찜할 관광지 ID
     * @throws BusinessException PLACE_010 — 이미 찜한 관광지
     * @throws BusinessException PLACE_001 — 존재하지 않는 관광지
     */
    @Transactional
    public void addLike(Long userId, Long placeId) {
        // 1. 중복 찜 fast-path (UNIQUE 제약보다 먼저 검증해 명확한 에러코드 응답)
        if (userLikeRepository.existsByUserIdAndPlaceId(userId, placeId)) {
            throw new BusinessException(ErrorCode.LIKE_ALREADY_EXISTS);
        }

        // 2. 관광지 존재 + ACTIVE 상태 확인 (HIDDEN/DELETED 관광지는 찜 불가)
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        if (place.getStatus() != PlaceStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }

        // 3. Account Proxy 조회 — JWT 필터에서 이미 서명 검증이 완료됐으므로 실제 SELECT 없이
        //    FK 바인딩용 프록시만 가져온다.
        //    탈퇴 보호는 @SQLRestriction이 아닌 JWT 블랙리스트(로그아웃 시 토큰 무효화)로 처리된다.
        //    getReferenceById는 DB를 조회하지 않아 @SQLRestriction(deleted_at IS NULL)이 실행되지 않는다.
        Account user = accountRepository.getReferenceById(userId);

        // 4. DB INSERT
        //    race condition 안전망: exists 체크를 통과한 동시 요청이 UNIQUE 제약을 위반하면
        //    DataIntegrityViolationException을 LIKE_ALREADY_EXISTS로 변환해 500을 방지한다.
        try {
            userLikeRepository.save(UserLike.of(user, place));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.LIKE_ALREADY_EXISTS);
        }

        // 5. Redis INCR + 캐시 무효화 — DB 커밋 이후에만 실행
        //    트랜잭션 롤백 시 Redis 상태가 어긋나지 않도록 afterCommit으로 이동.
        //    place.getLikeCount()는 트랜잭션 내에서 읽어 클로저에 캡처 (afterCommit에서 DB 재조회 없음)
        final int dbLikeCount = place.getLikeCount();
        registerAfterCommit(() -> {
            seedAndIncrement(placeId, dbLikeCount);
            evictDetailCache(placeId);
        });

        log.info("Place like added: userId={}, placeId={}", userId, placeId);
    }

    /**
     * 관광지 찜 취소
     * DELETE /api/v1/places/{placeId}/like
     *
     * <p>exists + delete 이중 조회 대신 단일 DELETE JPQL로 처리한다.
     * 삭제된 row 수가 0이면 찜 내역이 없는 것으로 판단해 LIKE_NOT_FOUND를 던진다.
     *
     * @param userId  인증된 사용자 ID
     * @param placeId 찜 취소할 관광지 ID
     * @throws BusinessException PLACE_011 — 찜하지 않은 관광지
     */
    @Transactional
    public void removeLike(Long userId, Long placeId) {
        int deleted = userLikeRepository.deleteByUserIdAndPlaceId(userId, placeId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.LIKE_NOT_FOUND);
        }

        // Redis 선시드를 위해 현재 DB like_count를 트랜잭션 내에서 읽어 캡처
        // (afterCommit에서 별도 DB 조회 없이 클로저로 전달)
        final int dbLikeCount = placeRepository.findById(placeId)
                .map(Place::getLikeCount)
                .orElse(0);

        // Redis DECR + 캐시 무효화 — DB 커밋 이후에만 실행
        registerAfterCommit(() -> {
            seedAndDecrement(placeId, dbLikeCount);
            evictDetailCache(placeId);
        });

        log.info("Place like removed: userId={}, placeId={}", userId, placeId);
    }

    /**
     * 특정 사용자가 해당 관광지를 찜했는지 확인.
     * PlaceService.findById에서 isLiked 계산 시 위임받아 사용된다.
     *
     * @param userId  사용자 ID (null이면 비로그인 → false)
     * @param placeId 관광지 ID
     * @return 찜 여부
     */
    @Transactional(readOnly = true)
    public boolean isLiked(Long userId, Long placeId) {
        if (userId == null) {
            return false;
        }
        return userLikeRepository.existsByUserIdAndPlaceId(userId, placeId);
    }

    // ── Redis 헬퍼 ────────────────────────────────────────────────────

    /**
     * Redis 카운터가 없으면 DB 값으로 선시드(SETNX)한 뒤 INCR.
     *
     * <p>동작 시나리오:
     * <ul>
     *   <li>키 없음: {@code SETNX key dbLikeCount} 성공 → {@code INCR key} → dbLikeCount + 1</li>
     *   <li>키 있음: {@code SETNX} 실패(유지) → {@code INCR key} → 현재값 + 1</li>
     * </ul>
     * SETNX와 INCR 사이 non-atomic 구간에서 동시 요청이 끌어들어도
     * 카운터는 Eventually Consistent 정책으로 허용한다.
     */
    private void seedAndIncrement(Long placeId, int dbLikeCount) {
        try {
            String key = PLACE_LIKE_COUNT_KEY + placeId;
            // 키가 없을 때만 DB 값으로 초기화 (SETNX)
            stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(dbLikeCount));
            stringRedisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.warn("Place like count seed+increment failed: placeId={}", placeId, e);
        }
    }

    /**
     * Redis 카운터가 없으면 DB 값으로 선시드(SETNX)한 뒤 DECR.
     * 0 미만 방지 로직 포함.
     */
    private void seedAndDecrement(Long placeId, int dbLikeCount) {
        try {
            String key = PLACE_LIKE_COUNT_KEY + placeId;
            // 키가 없을 때만 DB 값으로 초기화 (SETNX)
            stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(dbLikeCount));
            Long current = stringRedisTemplate.opsForValue().decrement(key);
            // 0 미만 방지: Eventually Consistent 정책, PHASE 5 배치에서 보정
            if (current != null && current < 0) {
                stringRedisTemplate.opsForValue().set(key, "0");
                log.warn("Place like count was negative, reset to 0: placeId={}", placeId);
            }
        } catch (Exception e) {
            log.warn("Place like count seed+decrement failed: placeId={}", placeId, e);
        }
    }

    /**
     * 찜하기/취소 후 상세 조회 캐시를 즉시 삭제.
     * PlaceCacheDto는 places.like_count DB 값을 스냅샷하므로,
     * 삭제하지 않으면 TTL(10분) 내내 stale likeCount가 응답에 노출된다.
     * 삭제 실패 시 자연 TTL 만료를 대기 (비즈니스 로직 차단 없이 warn 로그만 남김).
     */
    private void evictDetailCache(Long placeId) {
        try {
            stringRedisTemplate.delete(PLACE_DETAIL_CACHE_KEY + placeId);
            log.debug("Place detail cache evicted: placeId={}", placeId);
        } catch (Exception e) {
            log.warn("Place detail cache eviction failed: placeId={}", placeId, e);
        }
    }

    /**
     * DB 트랜잭션 커밋 성공 후 {@code action}을 실행하도록 등록한다.
     *
     * <p>Redis 카운터 변경과 캐시 무효화를 {@code afterCommit}으로 분리해
     * DB 롤백 시 Redis 상태가 어긋나는 것을 방지한다.
     * 활성 트랜잭션이 없는 경우(테스트 등)에는 즉시 실행한다.
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
            // 활성 트랜잭션이 없으면 즉시 실행 (테스트 환경 대비)
            action.run();
        }
    }
}
