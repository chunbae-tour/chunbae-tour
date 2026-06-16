package com.chunbaetour.domain.community.comment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.comment.entity.CommentStatus;
import com.chunbaetour.domain.community.common.PostType;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class CommentSoftDeleteByPostIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private EntityManager em;

    @Test
    @Transactional
    @DisplayName("softDeleteByPost — 해당 게시글의 ACTIVE 댓글만 DELETED 처리, 다른 게시글·postType·이미 DELETED는 무영향")
    void softDeleteByPost_onlyTargetActiveComments() {
        // 대상: post 10 / COMPANION ACTIVE 2건
        Comment a1 = commentRepository.save(Comment.create(10L, PostType.COMPANION, 1L, "a1"));
        Comment a2 = commentRepository.save(Comment.create(10L, PostType.COMPANION, 1L, "a2"));
        // 같은 post지만 이미 DELETED — 카운트/변경 제외
        Comment alreadyDeleted = Comment.create(10L, PostType.COMPANION, 1L, "del");
        alreadyDeleted.delete();
        commentRepository.save(alreadyDeleted);
        // 같은 postId(10)지만 postType 다름(FREE) — 무영향
        Comment freeSamePostId = commentRepository.save(Comment.create(10L, PostType.FREE, 1L, "free"));
        // 다른 postId(11) COMPANION — 무영향
        Comment otherPost = commentRepository.save(Comment.create(11L, PostType.COMPANION, 1L, "other"));
        em.flush();

        LocalDateTime now = LocalDateTime.now();
        int affected = commentRepository.softDeleteByPost(10L, PostType.COMPANION, now);

        // ACTIVE 2건만 영향
        assertThat(affected).isEqualTo(2);

        em.clear();
        assertThat(commentRepository.findById(a1.getId()).orElseThrow().getStatus())
                .isEqualTo(CommentStatus.DELETED);
        assertThat(commentRepository.findById(a2.getId()).orElseThrow().getStatus())
                .isEqualTo(CommentStatus.DELETED);
        assertThat(commentRepository.findById(a1.getId()).orElseThrow().getDeletedAt()).isNotNull();
        // 무영향 검증
        assertThat(commentRepository.findById(freeSamePostId.getId()).orElseThrow().getStatus())
                .isEqualTo(CommentStatus.ACTIVE);
        assertThat(commentRepository.findById(otherPost.getId()).orElseThrow().getStatus())
                .isEqualTo(CommentStatus.ACTIVE);
    }

    @Test
    @Transactional
    @DisplayName("softDeleteByPost — 루트 댓글과 대댓글(parentCommentId != null) 모두 일괄 삭제 + updatedAt 갱신")
    void softDeleteByPost_includesRepliesAndTouchesUpdatedAt() {
        Comment root = commentRepository.save(Comment.create(10L, PostType.COMPANION, 1L, "root"));
        Comment reply = commentRepository.save(
                Comment.create(10L, PostType.COMPANION, 2L, "reply", root.getId()));
        em.flush();

        LocalDateTime now = LocalDateTime.now();
        int affected = commentRepository.softDeleteByPost(10L, PostType.COMPANION, now);

        // 루트 + 대댓글 모두 삭제 (쿼리에 parentCommentId 필터 없음)
        assertThat(affected).isEqualTo(2);

        em.clear();
        Comment deletedReply = commentRepository.findById(reply.getId()).orElseThrow();
        assertThat(deletedReply.getStatus()).isEqualTo(CommentStatus.DELETED);
        assertThat(deletedReply.getParentCommentId()).isEqualTo(root.getId());
        // 벌크 UPDATE가 Auditing을 우회하므로 쿼리에서 직접 updatedAt을 갱신했는지 확인.
        // deletedAt·updatedAt 모두 같은 :now 파라미터라 저장값이 동일해야 한다(정밀도 무관).
        assertThat(deletedReply.getUpdatedAt()).isNotNull();
        assertThat(deletedReply.getUpdatedAt()).isEqualTo(deletedReply.getDeletedAt());
    }
}
