package com.chunbaetour.domain.like.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.like.dto.response.UserLikedTargetResponse;
import com.chunbaetour.domain.like.repository.UserLikedTargetQueryRepository;
import com.chunbaetour.domain.like.type.LikeTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * My-page liked target facade.
 *
 * <p>Keep target-type branching outside the controller so adding another liked domain later only
 * requires a new repository branch and DTO mapping, not another controller contract.
 */
@Service
@RequiredArgsConstructor
public class UserLikedTargetService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserLikedTargetQueryRepository queryRepository;

    @Transactional(readOnly = true)
    public Page<UserLikedTargetResponse> getLikedTargets(
            Long userId,
            LikeTargetType targetType,
            Pageable pageable
    ) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return switch (targetType) {
            case PLACE -> queryRepository.findLikedPlaces(userId, pageable);
            case MARKET -> queryRepository.findLikedMarkets(userId, pageable);
            case FESTIVAL -> queryRepository.findLikedFestivals(userId, pageable);
        };
    }
}
