package com.chunbaetour.domain.community.comment.service;

import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.community.comment.repository.CommentRepository.PostCommentCount;
import com.chunbaetour.domain.community.common.PostType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 댓글 수 조회 전용 서비스.
 *
 * <p>댓글 수는 비정규화 컬럼 없이 comments 테이블의 ACTIVE 행을 집계해 산출한다.
 * 별도 카운터 컬럼이 없어 동기화 drift가 없고 삭제/숨김 상태가 즉시 반영된다.
 * 게시글 도메인(자유·동행)이 목록·상세 응답에 댓글 수를 주입할 때 사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentCountService {

    private final CommentRepository commentRepository;

    /** 게시글 단건의 ACTIVE 댓글 수. */
    public long countByPost(Long postId, PostType postType) {
        return commentRepository.countActiveByPost(postId, postType);
    }

    /** 게시글 목록의 ACTIVE 댓글 수 일괄 집계 — postId → count. 없는 게시글은 맵에 미포함. */
    public Map<Long, Long> countByPosts(Collection<Long> postIds, PostType postType) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return commentRepository.countActiveByPostIds(List.copyOf(postIds), postType).stream()
                .collect(Collectors.toMap(PostCommentCount::getPostId, PostCommentCount::getCount));
    }
}
