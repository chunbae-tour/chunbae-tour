package com.chunbaetour.domain.community.comment.repository;

import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.comment.entity.CommentStatus;
import com.chunbaetour.domain.community.comment.entity.PostType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 커서 기반 페이지네이션: cursor가 null이면 첫 페이지(조건 무시),
    // 값이 있으면 해당 ID 이후 데이터만 조회 — OFFSET 방식 대비 대용량에서 성능 유지
    @Query("""
            SELECT c FROM Comment c
            WHERE c.postId = :postId
              AND c.postType = :postType
              AND c.status = :status
              AND (:cursor IS NULL OR c.id > :cursor)
            ORDER BY c.id ASC
            """)
    List<Comment> findByPost(
            @Param("postId") Long postId,
            @Param("postType") PostType postType,
            @Param("status") CommentStatus status,
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}
