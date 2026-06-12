package com.chunbaetour.domain.community.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.community.comment.dto.CommentCreateRequest;
import com.chunbaetour.domain.community.comment.dto.CommentCreateResponse;
import com.chunbaetour.domain.community.comment.dto.CommentGetListResponse;
import com.chunbaetour.domain.community.comment.dto.CommentUpdateRequest;
import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.comment.entity.CommentStatus;
import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.community.comment.repository.CommentRepository.ReplyCount;
import com.chunbaetour.domain.community.common.PostType;
import com.chunbaetour.domain.community.common.service.PostQueryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock AccountRepository accountRepository;
    @Mock PostQueryService postQueryService;
    @InjectMocks CommentService commentService;

    private static final Long AUTHOR_ID  = 1L;
    private static final Long POST_ID    = 10L;
    private static final Long COMMENT_ID = 100L;
    private static final PostType POST_TYPE = PostType.COMPANION;

    private Comment buildComment(Long id, Long parentId, CommentStatus status) {
        Comment c = Comment.create(POST_ID, POST_TYPE, AUTHOR_ID, "내용", parentId);
        ReflectionTestUtils.setField(c, "id", id);
        if (status == CommentStatus.DELETED) c.delete();
        return c;
    }

    private Account mockAccount(Long id) {
        Account account = Mockito.mock(Account.class);
        given(account.getId()).willReturn(id);
        return account;
    }

    private ReplyCount mockReplyCount(Long parentId, long count) {
        ReplyCount rc = Mockito.mock(ReplyCount.class);
        given(rc.getParentCommentId()).willReturn(parentId);
        given(rc.getCount()).willReturn(count);
        return rc;
    }

    // ── create ────────────────────────────────────────────────────────────

    @Test
    void create_루트_댓글_성공() {
        Account author = mockAccount(AUTHOR_ID);
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        given(commentRepository.save(any())).willAnswer(inv -> {
            Comment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", COMMENT_ID);
            return c;
        });

        CommentCreateResponse response = commentService.create(
                AUTHOR_ID, POST_ID, POST_TYPE, new CommentCreateRequest("내용", null));

        assertThat(response.commentId()).isEqualTo(COMMENT_ID);
    }

    @Test
    void create_대댓글_성공() {
        Long parentId = 50L;
        Comment parent = buildComment(parentId, null, CommentStatus.ACTIVE);
        Account author = mockAccount(AUTHOR_ID);
        given(commentRepository.findById(parentId)).willReturn(Optional.of(parent));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        given(commentRepository.save(any())).willAnswer(inv -> {
            Comment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", COMMENT_ID);
            return c;
        });

        CommentCreateResponse response = commentService.create(
                AUTHOR_ID, POST_ID, POST_TYPE, new CommentCreateRequest("내용", parentId));

        assertThat(response.parentCommentId()).isEqualTo(parentId);
    }

    @Test
    void create_대댓글의_대댓글_COMMENT_REPLY_DEPTH_EXCEEDED() {
        Long grandParentId = 40L;
        Long parentId = 50L;
        // parent가 이미 대댓글 (parentCommentId != null) — account.getId()는 예외 전에 호출 안 됨
        Comment parent = buildComment(parentId, grandParentId, CommentStatus.ACTIVE);
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(Mockito.mock(Account.class)));
        given(commentRepository.findById(parentId)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.create(
                AUTHOR_ID, POST_ID, POST_TYPE, new CommentCreateRequest("내용", parentId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_REPLY_DEPTH_EXCEEDED);
    }

    @Test
    void create_삭제된_부모에_대댓글_COMMENT_ALREADY_DELETED() {
        Long parentId = 50L;
        Comment deletedParent = buildComment(parentId, null, CommentStatus.DELETED);
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(Mockito.mock(Account.class)));
        given(commentRepository.findById(parentId)).willReturn(Optional.of(deletedParent));

        assertThatThrownBy(() -> commentService.create(
                AUTHOR_ID, POST_ID, POST_TYPE, new CommentCreateRequest("내용", parentId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_ALREADY_DELETED);
    }

    @Test
    void create_다른_게시글의_댓글을_parent로_COMMENT_NOT_FOUND() {
        Long parentId = 50L;
        // parent가 다른 게시글 소속
        Comment parent = Comment.create(999L, POST_TYPE, AUTHOR_ID, "내용", null);
        ReflectionTestUtils.setField(parent, "id", parentId);
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(Mockito.mock(Account.class)));
        given(commentRepository.findById(parentId)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.create(
                AUTHOR_ID, POST_ID, POST_TYPE, new CommentCreateRequest("내용", parentId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    // ── findAll ───────────────────────────────────────────────────────────

    @Test
    void findAll_hasNext_true() {
        int size = 2;
        List<Comment> roots = List.of(
                buildComment(1L, null, CommentStatus.ACTIVE),
                buildComment(2L, null, CommentStatus.ACTIVE),
                buildComment(3L, null, CommentStatus.ACTIVE));
        given(commentRepository.findRootComments(any(), any(), any(), any(Pageable.class))).willReturn(roots);
        given(accountRepository.findAllById(any())).willReturn(List.of());
        given(commentRepository.countRepliesByParentIds(any())).willReturn(List.of());

        CursorPageResponse<CommentGetListResponse> result =
                commentService.findAll(POST_ID, POST_TYPE, null, size);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.content()).hasSize(size);
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    void findAll_hasNext_false() {
        int size = 5;
        List<Comment> roots = List.of(buildComment(1L, null, CommentStatus.ACTIVE));
        given(commentRepository.findRootComments(any(), any(), any(), any(Pageable.class))).willReturn(roots);
        given(accountRepository.findAllById(any())).willReturn(List.of());
        given(commentRepository.countRepliesByParentIds(any())).willReturn(List.of());

        CursorPageResponse<CommentGetListResponse> result =
                commentService.findAll(POST_ID, POST_TYPE, null, size);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void findAll_삭제된_루트_댓글_작성자_조회_스킵() {
        // DELETED 루트 댓글은 authorIds에서 제외 — accountRepository 호출 안 됨 (ternary guard)
        int size = 5;
        Comment deleted = buildComment(1L, null, CommentStatus.DELETED);
        ReplyCount rc = mockReplyCount(1L, 2L);
        given(commentRepository.findRootComments(any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of(deleted));
        given(commentRepository.countRepliesByParentIds(any())).willReturn(List.of(rc));

        CursorPageResponse<CommentGetListResponse> result =
                commentService.findAll(POST_ID, POST_TYPE, null, size);

        assertThat(result.content()).hasSize(1);
        then(accountRepository).should(never()).findAllById(any());
    }

    // ── findReplies ───────────────────────────────────────────────────────

    @Test
    void findReplies_성공() {
        Comment parent = buildComment(COMMENT_ID, null, CommentStatus.ACTIVE);
        Comment reply1 = buildComment(200L, COMMENT_ID, CommentStatus.ACTIVE);
        Comment reply2 = buildComment(201L, COMMENT_ID, CommentStatus.ACTIVE);
        given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(parent));
        given(commentRepository.findByParentCommentIdAndStatusOrderByIdAsc(COMMENT_ID, CommentStatus.ACTIVE))
                .willReturn(List.of(reply1, reply2));
        given(accountRepository.findAllById(any())).willReturn(List.of());

        List<CommentGetListResponse> replies = commentService.findReplies(POST_ID, POST_TYPE, COMMENT_ID);

        assertThat(replies).hasSize(2);
    }

    @Test
    void findReplies_대댓글에_findReplies_COMMENT_NOT_FOUND() {
        Long parentId = 50L;
        // commentId가 이미 대댓글 (parentCommentId != null)
        Comment reply = buildComment(COMMENT_ID, parentId, CommentStatus.ACTIVE);
        given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(reply));

        assertThatThrownBy(() -> commentService.findReplies(POST_ID, POST_TYPE, COMMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    void findReplies_다른_게시글_댓글_COMMENT_NOT_FOUND() {
        Comment comment = Comment.create(999L, POST_TYPE, AUTHOR_ID, "내용", null);
        ReflectionTestUtils.setField(comment, "id", COMMENT_ID);
        given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.findReplies(POST_ID, POST_TYPE, COMMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_성공() {
        Comment comment = buildComment(COMMENT_ID, null, CommentStatus.ACTIVE);
        given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

        commentService.update(AUTHOR_ID, POST_ID, POST_TYPE, COMMENT_ID,
                new CommentUpdateRequest("수정된내용"));

        assertThat(comment.getContent()).isEqualTo("수정된내용");
    }

    @Test
    void update_타인_COMMENT_FORBIDDEN() {
        Long otherId = 99L;
        Comment comment = buildComment(COMMENT_ID, null, CommentStatus.ACTIVE);
        given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(otherId, POST_ID, POST_TYPE, COMMENT_ID,
                new CommentUpdateRequest("수정된내용")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_FORBIDDEN);
    }

    @Test
    void update_이미삭제됨_COMMENT_ALREADY_DELETED() {
        Comment comment = buildComment(COMMENT_ID, null, CommentStatus.DELETED);
        given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(AUTHOR_ID, POST_ID, POST_TYPE, COMMENT_ID,
                new CommentUpdateRequest("수정된내용")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_ALREADY_DELETED);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_성공() {
        Comment comment = buildComment(COMMENT_ID, null, CommentStatus.ACTIVE);
        given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

        commentService.delete(AUTHOR_ID, POST_ID, POST_TYPE, COMMENT_ID);

        assertThat(comment.getStatus()).isEqualTo(CommentStatus.DELETED);
    }

    @Test
    void delete_타인_COMMENT_FORBIDDEN() {
        Long otherId = 99L;
        Comment comment = buildComment(COMMENT_ID, null, CommentStatus.ACTIVE);
        given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(otherId, POST_ID, POST_TYPE, COMMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_FORBIDDEN);
    }

    @Test
    void delete_이미삭제됨_COMMENT_ALREADY_DELETED() {
        Comment comment = buildComment(COMMENT_ID, null, CommentStatus.DELETED);
        given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(AUTHOR_ID, POST_ID, POST_TYPE, COMMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_ALREADY_DELETED);
    }
}
