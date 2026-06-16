package com.chunbaetour.domain.festival.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.like.service.LikeTargetValidator;
import com.chunbaetour.domain.like.type.LikeTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates that a FESTIVAL like target points to an ACTIVE festival.
 *
 * <p>Hidden/deleted festivals are treated as not found for the public like API so users cannot keep
 * interacting with targets that are no longer publicly visible.
 */
@Component
@RequiredArgsConstructor
public class FestivalLikeTargetValidator implements LikeTargetValidator {

    private final FestivalRepository festivalRepository;

    @Override
    public boolean supports(LikeTargetType targetType) {
        return targetType == LikeTargetType.FESTIVAL;
    }

    @Override
    public void validateTarget(Long targetId) {
        Festival festival = festivalRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FESTIVAL_NOT_FOUND));
        if (!festival.isActive()) {
            throw new BusinessException(ErrorCode.FESTIVAL_NOT_FOUND);
        }
    }
}
