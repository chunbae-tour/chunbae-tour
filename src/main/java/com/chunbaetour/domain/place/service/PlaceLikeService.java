package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.dto.response.UserLikedPlaceResponse;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.like.service.UserLikeService;
import com.chunbaetour.domain.like.type.LikeTargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관광지 찜하기 / 찜 취소 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceLikeService {

    private final UserLikeService userLikeService;
    private final PlaceRepository placeRepository;
    private final PlaceQueryRepository placeQueryRepository;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String SEED_AND_INCR_SCRIPT = 
            "local key = KEYS[1]\n" +
            "if redis.call('EXISTS', key) == 0 then\n" +
            "  redis.call('SET', key, ARGV[1])\n" +
            "end\n" +
            "return redis.call('INCR', key)";

    private static final String SEED_AND_DECR_SCRIPT = 
            "local key = KEYS[1]\n" +
            "if redis.call('EXISTS', key) == 0 then\n" +
            "  redis.call('SET', key, ARGV[1])\n" +
            "end\n" +
            "local current = redis.call('DECR', key)\n" +
            "if current < 0 then\n" +
            "  redis.call('SET', key, 0)\n" +
            "  return 0\n" +
            "end\n" +
            "return current";

    private static final DefaultRedisScript<Long> REDIS_SCRIPT_INCR =
            new DefaultRedisScript<>(SEED_AND_INCR_SCRIPT, Long.class);

    private static final DefaultRedisScript<Long> REDIS_SCRIPT_DECR =
            new DefaultRedisScript<>(SEED_AND_DECR_SCRIPT, Long.class);

    @Transactional
    public void addLike(Long userId, Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        if (place.getStatus() != PlaceStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }

        boolean success = userLikeService.addLike(userId, LikeTargetType.PLACE, placeId);
        if (!success) {
            throw new BusinessException(ErrorCode.LIKE_ALREADY_EXISTS);
        }

        final int dbLikeCount = place.getLikeCount();
        registerAfterCommit(() -> {
            seedAndIncrement(placeId, dbLikeCount);
            evictDetailCache(placeId);
        });

        log.info("Place like added: userId={}, placeId={}", userId, placeId);
    }

    @Transactional
    public void removeLike(Long userId, Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        if (place.getStatus() != PlaceStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
        int dbLikeCount = place.getLikeCount();

        boolean success = userLikeService.removeLike(userId, LikeTargetType.PLACE, placeId);
        if (!success) {
            throw new BusinessException(ErrorCode.LIKE_NOT_FOUND);
        }

        registerAfterCommit(() -> {
            seedAndDecrement(placeId, dbLikeCount);
            evictDetailCache(placeId);
        });

        log.info("Place like removed: userId={}, placeId={}", userId, placeId);
    }

    @Transactional(readOnly = true)
    public boolean isLiked(Long userId, Long placeId) {
        return userLikeService.isLiked(userId, LikeTargetType.PLACE, placeId);
    }

    @Transactional(readOnly = true)
    public Page<UserLikedPlaceResponse> getUserLikedPlaces(Long userId, Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return placeQueryRepository.findUserLikedPlaces(userId, pageable);
    }

    // ── Redis 헬퍼 ────────────────────────────────────────────────────

    /**
     * Redis 카운터가 없으면 DB 값으로 선시드(SETNX)한 뒤 INCR를 Lua 스크립트로 원자적 처리.
     */
    private void seedAndIncrement(Long placeId, int dbLikeCount) {
        try {
            String key = PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + placeId;
            stringRedisTemplate.execute(
                    REDIS_SCRIPT_INCR,
                    java.util.Collections.singletonList(key),
                    String.valueOf(dbLikeCount)
            );
        } catch (Exception e) {
            log.warn("Place like count seed+increment failed: placeId={}", placeId, e);
        }
    }

    /**
     * Redis 카운터가 없으면 DB 값으로 선시드(SETNX)한 뒤 DECR를 Lua 스크립트로 원자적 처리.
     * 0 미만 방지 로직 포함.
     */
    private void seedAndDecrement(Long placeId, int dbLikeCount) {
        try {
            String key = PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + placeId;
            stringRedisTemplate.execute(
                    REDIS_SCRIPT_DECR,
                    java.util.Collections.singletonList(key),
                    String.valueOf(dbLikeCount)
            );
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
            stringRedisTemplate.delete(PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + placeId);
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
