package com.chunbaetour.domain.community.comment.event;

import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.community.common.event.PostDeletedEvent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 게시글 삭제 후 해당 게시글의 댓글을 일괄 soft-delete한다.
 *
 * <p>AFTER_COMMIT 시점 — 게시글 삭제 트랜잭션이 커밋된 뒤 별도 트랜잭션에서 실행해 댓글 정리 실패가
 * 게시글 삭제를 롤백시키지 않게 한다(디커플링). 커밋 후 실행이라 새 트랜잭션이 필요하므로
 * {@code @Transactional(REQUIRES_NEW)}를 명시한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentPostDeletedHandler {

    private final CommentRepository commentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostDeleted(PostDeletedEvent event) {
        int deleted = commentRepository.softDeleteByPost(
                event.postId(), event.postType(), LocalDateTime.now());
        log.info("PostDeletedEvent 처리 — 댓글 일괄 삭제: postId={}, postType={}, deletedCount={}",
                event.postId(), event.postType(), deleted);
    }
}
