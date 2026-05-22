package com.chunbaetour.domain.community.common.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.community.comment.entity.PostType;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostQueryService {

    private final CompanionPostRepository companionPostRepository;
    private final FreePostRepository freePostRepository;

    // ACTIVE 상태인 게시글만 허용 — 삭제·숨김·마감 게시글에 새 댓글 차단
    @Transactional(readOnly = true)
    public void validateCommentable(Long postId, PostType postType) {
        switch (postType) {
            case COMPANION -> {
                CompanionPost post = companionPostRepository.findById(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
                if (post.getStatus() != CompanionPostStatus.ACTIVE) {
                    throw new BusinessException(ErrorCode.POST_NOT_FOUND);
                }
            }
            case FREE -> {
                FreePost post = freePostRepository.findById(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
                if (post.getStatus() != FreePostStatus.ACTIVE) {
                    throw new BusinessException(ErrorCode.POST_NOT_FOUND);
                }
            }
        }
    }

    // CLOSED(마감) 게시글은 기존 댓글 조회 허용 — 삭제·숨김은 차단
    @Transactional(readOnly = true)
    public void validateExists(Long postId, PostType postType) {
        switch (postType) {
            case COMPANION -> {
                CompanionPost post = companionPostRepository.findById(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
                if (post.getStatus() == CompanionPostStatus.DELETED
                        || post.getStatus() == CompanionPostStatus.HIDDEN) {
                    throw new BusinessException(ErrorCode.POST_NOT_FOUND);
                }
            }
            case FREE -> {
                FreePost post = freePostRepository.findById(postId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
                if (post.getStatus() == FreePostStatus.DELETED
                        || post.getStatus() == FreePostStatus.HIDDEN) {
                    throw new BusinessException(ErrorCode.POST_NOT_FOUND);
                }
            }
        }
    }
}
