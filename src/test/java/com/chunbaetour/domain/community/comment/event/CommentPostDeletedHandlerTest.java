package com.chunbaetour.domain.community.comment.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.community.common.PostType;
import com.chunbaetour.domain.community.common.event.PostDeletedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentPostDeletedHandlerTest {

    @Mock CommentRepository commentRepository;
    @InjectMocks CommentPostDeletedHandler handler;

    @Test
    void 게시글_삭제_이벤트_수신시_해당_게시글_댓글_일괄삭제() {
        given(commentRepository.softDeleteByPost(eq(10L), eq(PostType.COMPANION), any(LocalDateTime.class)))
                .willReturn(3);

        handler.handlePostDeleted(new PostDeletedEvent(10L, PostType.COMPANION));

        then(commentRepository).should()
                .softDeleteByPost(eq(10L), eq(PostType.COMPANION), any(LocalDateTime.class));
    }

    @Test
    void FREE_게시글_이벤트도_postType_전달() {
        given(commentRepository.softDeleteByPost(eq(20L), eq(PostType.FREE), any(LocalDateTime.class)))
                .willReturn(0);

        handler.handlePostDeleted(new PostDeletedEvent(20L, PostType.FREE));

        then(commentRepository).should()
                .softDeleteByPost(eq(20L), eq(PostType.FREE), any(LocalDateTime.class));
    }
}
