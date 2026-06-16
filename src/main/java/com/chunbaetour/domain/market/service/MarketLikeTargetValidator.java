package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.like.service.LikeTargetValidator;
import com.chunbaetour.domain.like.type.LikeTargetType;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates that a MARKET like target points to an existing traditional market.
 *
 * <p>TraditionalMarket currently has no soft-delete status, so existence is the target validity
 * rule for likes.
 */
@Component
@RequiredArgsConstructor
public class MarketLikeTargetValidator implements LikeTargetValidator {

    private final TraditionalMarketRepository marketRepository;

    @Override
    public boolean supports(LikeTargetType targetType) {
        return targetType == LikeTargetType.MARKET;
    }

    @Override
    public void validateTarget(Long targetId) {
        if (!marketRepository.existsById(targetId)) {
            throw new BusinessException(ErrorCode.MARKET_NOT_FOUND);
        }
    }
}
