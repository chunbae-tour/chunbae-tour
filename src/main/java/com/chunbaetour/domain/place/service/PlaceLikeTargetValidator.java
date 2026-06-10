package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.like.service.LikeTargetValidator;
import com.chunbaetour.domain.like.type.LikeTargetType;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaceLikeTargetValidator implements LikeTargetValidator {

    private final PlaceRepository placeRepository;

    @Override
    public boolean supports(LikeTargetType targetType) {
        return targetType == LikeTargetType.PLACE;
    }

    @Override
    public void validateTarget(Long targetId) {
        Place place = placeRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        
        if (place.getStatus() != PlaceStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
    }
}
