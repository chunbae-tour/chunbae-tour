package com.chunbaetour.domain.festival.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.like.service.UserLikeService;
import com.chunbaetour.domain.like.type.LikeTargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Festival like use cases.
 *
 * <p>The common {@link UserLikeService} handles duplicate protection and persistence, while this
 * service keeps festival visibility checks and domain-specific errors close to the festival API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalLikeService {

    private final UserLikeService userLikeService;
    private final FestivalRepository festivalRepository;

    @Transactional
    public void addLike(Long userId, Long festivalId) {
        boolean success = userLikeService.addLike(userId, LikeTargetType.FESTIVAL, festivalId);
        if (!success) {
            throw new BusinessException(ErrorCode.LIKE_ALREADY_EXISTS);
        }
        log.info("Festival like added: userId={}, festivalId={}", userId, festivalId);
    }

    @Transactional
    public void removeLike(Long userId, Long festivalId) {
        Festival festival = festivalRepository.findById(festivalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FESTIVAL_NOT_FOUND));
        if (!festival.isActive()) {
            throw new BusinessException(ErrorCode.FESTIVAL_NOT_FOUND);
        }

        boolean success = userLikeService.removeLike(userId, LikeTargetType.FESTIVAL, festivalId);
        if (!success) {
            throw new BusinessException(ErrorCode.LIKE_NOT_FOUND);
        }
        log.info("Festival like removed: userId={}, festivalId={}", userId, festivalId);
    }
}
