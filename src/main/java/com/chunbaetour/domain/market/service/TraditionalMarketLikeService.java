package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.like.service.UserLikeService;
import com.chunbaetour.domain.like.type.LikeTargetType;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traditional market like use cases.
 *
 * <p>The actual like row is stored by {@link UserLikeService}. This service keeps market-specific
 * error mapping and endpoint-level intent separate from the common persistence component.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraditionalMarketLikeService {

    private final UserLikeService userLikeService;
    private final TraditionalMarketRepository marketRepository;

    @Transactional
    public void addLike(Long userId, Long marketId) {
        boolean success = userLikeService.addLike(userId, LikeTargetType.MARKET, marketId);
        if (!success) {
            throw new BusinessException(ErrorCode.LIKE_ALREADY_EXISTS);
        }
        log.info("Traditional market like added: userId={}, marketId={}", userId, marketId);
    }

    @Transactional
    public void removeLike(Long userId, Long marketId) {
        if (!marketRepository.existsById(marketId)) {
            throw new BusinessException(ErrorCode.MARKET_NOT_FOUND);
        }

        boolean success = userLikeService.removeLike(userId, LikeTargetType.MARKET, marketId);
        if (!success) {
            throw new BusinessException(ErrorCode.LIKE_NOT_FOUND);
        }
        log.info("Traditional market like removed: userId={}, marketId={}", userId, marketId);
    }
}
