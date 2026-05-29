package com.chunbaetour.domain.community.comment.repository;

import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.comment.entity.CommentStatus;
import com.chunbaetour.domain.community.common.PostType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 자동 숨김 직렬화용 비관적 쓰기 락 — 동시 신고로 인한 임계값 경합 방지 (KAN-93). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Comment c WHERE c.id = :id")
    Optional<Comment> findByIdForUpdate(@Param("id") Long id);

    // 루트 댓글 cursor 페이징 — ACTIVE + 대댓글이 남아있는 DELETED(삭제된 댓글 placeholder)
    @Query("""
            SELECT c FROM Comment c
            WHERE c.postId = :postId
              AND c.postType = :postType
              AND c.parentCommentId IS NULL
              AND (c.status = 'ACTIVE'
                   OR (c.status = 'DELETED' AND EXISTS (
                       SELECT 1 FROM Comment r
                       WHERE r.parentCommentId = c.id AND r.status = 'ACTIVE'
                   )))
              AND (:cursor IS NULL OR c.id > :cursor)
            ORDER BY c.id ASC
            """)
    List<Comment> findRootComments(
            @Param("postId") Long postId,
            @Param("postType") PostType postType,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    // 루트 댓글 id 목록의 대댓글 수 일괄 집계 — N+1 방지
    @Query("SELECT c.parentCommentId, COUNT(c) FROM Comment c " +
           "WHERE c.parentCommentId IN :parentIds AND c.status = 'ACTIVE' " +
           "GROUP BY c.parentCommentId")
    List<Object[]> countRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    // 특정 루트 댓글의 대댓글 전체 조회 (더보기)
    List<Comment> findByParentCommentIdAndStatusOrderByIdAsc(Long parentCommentId, CommentStatus status);
}
