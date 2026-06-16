package com.chunbaetour.domain.community.common.event;

import com.chunbaetour.domain.community.common.PostType;

/**
 * 게시글 삭제 시 발행. 댓글 등 하위 콘텐츠 정리를 이벤트로 디커플링한다.
 * companion·free 게시판이 발행하고, comment 도메인이 구독해 일괄 soft-delete한다.
 */
public record PostDeletedEvent(Long postId, PostType postType) {
}
