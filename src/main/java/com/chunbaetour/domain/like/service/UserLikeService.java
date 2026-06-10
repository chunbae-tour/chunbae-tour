package com.chunbaetour.domain.like.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.like.entity.UserLike;
import com.chunbaetour.domain.like.repository.UserLikeRepository;
import com.chunbaetour.domain.like.type.LikeTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserLikeService {

    private final UserLikeRepository userLikeRepository;
    private final AccountRepository accountRepository;

    /**
     * 찜 추가 (DB 저장 로직)
     * 경쟁 상태(race condition)에서 UNIQUE 제약 위반 발생 시 false 반환.
     * 존재하는지 여부를 빠르게 판단하기 위해 호출부에서 먼저 체크하는 것을 권장.
     */
    @Transactional
    public boolean addLike(Long userId, LikeTargetType targetType, Long targetId) {
        if (userLikeRepository.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)) {
            return false;
        }

        Account user = accountRepository.getReferenceById(userId);

        try {
            userLikeRepository.save(UserLike.of(user, targetType, targetId));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    /**
     * 찜 취소 (DB 삭제 로직)
     */
    @Transactional
    public boolean removeLike(Long userId, LikeTargetType targetType, Long targetId) {
        int deleted = userLikeRepository.deleteByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
        return deleted > 0;
    }

    @Transactional(readOnly = true)
    public boolean isLiked(Long userId, LikeTargetType targetType, Long targetId) {
        if (userId == null) return false;
        return userLikeRepository.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
    }

    @Transactional(readOnly = true)
    public Page<Long> getLikedTargetIds(Long userId, LikeTargetType targetType, Pageable pageable) {
        return userLikeRepository.findTargetIdsByUserIdAndTargetType(userId, targetType, pageable);
    }
}
