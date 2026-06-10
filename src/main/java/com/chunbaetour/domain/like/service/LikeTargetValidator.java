package com.chunbaetour.domain.like.service;

import com.chunbaetour.domain.like.type.LikeTargetType;

public interface LikeTargetValidator {
    boolean supports(LikeTargetType targetType);
    void validateTarget(Long targetId);
}
