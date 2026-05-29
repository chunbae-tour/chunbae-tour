package com.chunbaetour.domain.community.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.community.comment.dto.CommentCreateRequest;
import com.chunbaetour.domain.community.comment.dto.CommentCreateResponse;
import com.chunbaetour.domain.community.comment.dto.CommentGetListResponse;
import com.chunbaetour.domain.community.comment.dto.CommentUpdateRequest;
import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.comment.entity.CommentStatus;
import com.chunbaetour.domain.community.common.PostType;
import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.community.comment.service.CommentService;
import com.chunbaetour.domain.community.common.service.PostQueryService;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PostQueryService postQueryService;

    @InjectMocks
    private CommentService commentService;

    private Account author;

    @BeforeEach
    void setUp() {
        author = Account.registerUser("test@example.com", "hashed", "춘배여행자");
        ReflectionTestUtils.setField(author, "id", 1L);
    }

    // ── 댓글 작성 ─────────────────────────────────────────────────────────

    @Test
    void 댓글_작성_성공() {
        Comment comment = Comment.create(1L, PostType.FREE, 1L, "좋은 글이에요!");
        given(accountRepository.findById(1L)).willReturn(Optional.of(author));
        given(commentRepository.save(any())).willReturn(comment);

        CommentCreateResponse response = commentService.create(1L, 1L, PostType.FREE,
                new CommentCreateRequest("좋은 글이에요!", null));

        assertThat(response.content()).isEqualTo("좋은 글이에요!");
        assertThat(response.writer().nickname()).isEqualTo("춘배여행자");
    }

    @Test
    void 대댓글_작성_성공() {
        Comment parent = Comment.create(1L, PostType.FREE, 1L, "부모 댓글");
        ReflectionTestUtils.setField(parent, "id", 10L);
        Comment reply = Comment.create(1L, PostType.FREE, 1L, "대댓글이에요!", 10L);
        given(accountRepository.findById(1L)).willReturn(Optional.of(author));
        given(commentRepository.findById(10L)).willReturn(Optional.of(parent));
        given(commentRepository.save(any())).willReturn(reply);

        CommentCreateResponse response = commentService.create(1L, 1L, PostType.FREE,
                new CommentCreateRequest("대댓글이에요!", 10L));

        assertThat(response.content()).isEqualTo("대댓글이에요!");
        assertThat(response.parentCommentId()).isEqualTo(10L);
    }

    @Test
    void 대댓글에_대댓글_작성_400() {
        Comment reply = Comment.create(1L, PostType.FREE, 1L, "대댓글", 5L);
        ReflectionTestUtils.setField(reply, "id", 20L);
        given(accountRepository.findById(1L)).willReturn(Optional.of(author));
        given(commentRepository.findById(20L)).willReturn(Optional.of(reply));

        assertThatThrownBy(() -> commentService.create(1L, 1L, PostType.FREE,
                new CommentCreateRequest("2depth 대댓글", 20L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMENT_REPLY_DEPTH_EXCEEDED));
    }

    @Test
    void 삭제된_부모_댓글에_대댓글_작성_400() {
        Comment deletedParent = Comment.create(1L, PostType.FREE, 1L, "삭제된 부모");
        ReflectionTestUtils.setField(deletedParent, "id", 10L);
        deletedParent.delete();
        given(accountRepository.findById(1L)).willReturn(Optional.of(author));
        given(commentRepository.findById(10L)).willReturn(Optional.of(deletedParent));

        assertThatThrownBy(() -> commentService.create(1L, 1L, PostType.FREE,
                new CommentCreateRequest("대댓글 시도", 10L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMENT_ALREADY_DELETED));
    }

    @Test
    void 댓글_작성_존재하지않는_사용자_404() {
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(999L, 1L, PostType.FREE,
                new CommentCreateRequest("내용", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void 동행_게시판_댓글_작성() {
        Comment comment = Comment.create(2L, PostType.COMPANION, 1L, "같이 가요!");
        given(accountRepository.findById(1L)).willReturn(Optional.of(author));
        given(commentRepository.save(any())).willReturn(comment);

        CommentCreateResponse response = commentService.create(1L, 2L, PostType.COMPANION,
                new CommentCreateRequest("같이 가요!", null));

        assertThat(response.content()).isEqualTo("같이 가요!");
    }

    // ── 루트 댓글 목록 조회 ───────────────────────────────────────────────

    @Test
    void 댓글_목록_빈_결과() {
        given(commentRepository.findRootComments(1L, PostType.FREE, null, Pageable.ofSize(11)))
                .willReturn(List.of());
        given(accountRepository.findAllById(any())).willReturn(List.of());

        CursorPageResponse<CommentGetListResponse> result = commentService.findAll(1L, PostType.FREE, null, 10);

        assertThat(result.content()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 댓글_목록_hasNext_true() {
        List<Comment> comments = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(i -> {
                    Comment c = Comment.create(1L, PostType.FREE, 1L, "댓글" + i);
                    ReflectionTestUtils.setField(c, "id", (long) i);
                    return c;
                })
                .toList();
        given(commentRepository.findRootComments(1L, PostType.FREE, null, Pageable.ofSize(11)))
                .willReturn(comments);
        given(accountRepository.findAllById(any())).willReturn(List.of(author));
        given(commentRepository.countRepliesByParentIds(any())).willReturn(List.of());

        CursorPageResponse<CommentGetListResponse> result = commentService.findAll(1L, PostType.FREE, null, 10);

        assertThat(result.content()).hasSize(10);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    void 삭제된_루트_댓글_placeholder_표시() {
        Comment deleted = Comment.create(1L, PostType.FREE, 1L, "삭제될 내용");
        ReflectionTestUtils.setField(deleted, "id", 1L);
        deleted.delete();

        given(commentRepository.findRootComments(1L, PostType.FREE, null, Pageable.ofSize(11)))
                .willReturn(List.of(deleted));
        given(accountRepository.findAllById(any())).willReturn(List.of());
        given(commentRepository.countRepliesByParentIds(any())).willReturn(List.of());

        CursorPageResponse<CommentGetListResponse> result = commentService.findAll(1L, PostType.FREE, null, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).deleted()).isTrue();
        assertThat(result.content().get(0).content()).isEqualTo("(삭제된 댓글)");
        assertThat(result.content().get(0).writer()).isNull();
    }

    @Test
    void 삭제된_루트_댓글_replyCount_포함() {
        Comment deleted = Comment.create(1L, PostType.FREE, 1L, "삭제될 내용");
        ReflectionTestUtils.setField(deleted, "id", 1L);
        deleted.delete();

        // countRepliesByParentIds 반환값 — Object[] { parentCommentId, count }
        List<Object[]> countResult = new java.util.ArrayList<>();
        countResult.add(new Object[]{1L, 2L});

        given(commentRepository.findRootComments(1L, PostType.FREE, null, Pageable.ofSize(11)))
                .willReturn(List.of(deleted));
        given(accountRepository.findAllById(any())).willReturn(List.of());
        given(commentRepository.countRepliesByParentIds(any())).willReturn(countResult);

        CursorPageResponse<CommentGetListResponse> result = commentService.findAll(1L, PostType.FREE, null, 10);

        assertThat(result.content().get(0).replyCount()).isEqualTo(2L);
    }

    // ── 대댓글 더보기 ─────────────────────────────────────────────────────

    @Test
    void 대댓글_조회_성공() {
        Comment parent = Comment.create(1L, PostType.FREE, 1L, "루트 댓글");
        ReflectionTestUtils.setField(parent, "id", 1L);
        Comment reply = Comment.create(1L, PostType.FREE, 2L, "대댓글", 1L);
        ReflectionTestUtils.setField(reply, "id", 2L);

        given(commentRepository.findById(1L)).willReturn(Optional.of(parent));
        given(commentRepository.findByParentCommentIdAndStatusOrderByIdAsc(1L, CommentStatus.ACTIVE))
                .willReturn(List.of(reply));
        given(accountRepository.findAllById(any())).willReturn(List.of(author));

        List<CommentGetListResponse> result = commentService.findReplies(1L, PostType.FREE, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).parentCommentId()).isEqualTo(1L);
    }

    @Test
    void 대댓글에_대댓글_조회_요청_404() {
        Comment reply = Comment.create(1L, PostType.FREE, 1L, "대댓글", 5L);
        ReflectionTestUtils.setField(reply, "id", 10L);
        given(commentRepository.findById(10L)).willReturn(Optional.of(reply));

        assertThatThrownBy(() -> commentService.findReplies(1L, PostType.FREE, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }

    // ── 댓글 수정 ─────────────────────────────────────────────────────────

    @Test
    void 댓글_수정_성공() {
        Comment comment = Comment.create(1L, PostType.FREE, 1L, "원본 내용");
        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        commentService.update(1L, 1L, PostType.FREE, 1L, new CommentUpdateRequest("수정된 내용"));

        assertThat(comment.getContent()).isEqualTo("수정된 내용");
    }

    @Test
    void 댓글_수정_타인_403() {
        Comment comment = Comment.create(1L, PostType.FREE, 1L, "원본 내용");
        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(2L, 1L, PostType.FREE, 1L, new CommentUpdateRequest("수정 시도")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMENT_FORBIDDEN));
    }

    @Test
    void 삭제된_댓글_수정_400() {
        Comment comment = Comment.create(1L, PostType.FREE, 1L, "삭제된 댓글");
        comment.delete();
        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(1L, 1L, PostType.FREE, 1L, new CommentUpdateRequest("수정 시도")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMENT_ALREADY_DELETED));
    }

    @Test
    void 존재하지_않는_댓글_수정_404() {
        given(commentRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.update(1L, 1L, PostType.FREE, 99L, new CommentUpdateRequest("내용")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }

    @Test
    void 존재하지_않는_댓글_삭제_404() {
        given(commentRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.delete(1L, 1L, PostType.FREE, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }

    // ── 댓글 삭제 ─────────────────────────────────────────────────────────

    @Test
    void 댓글_삭제_성공() {
        Comment comment = Comment.create(1L, PostType.FREE, 1L, "삭제될 댓글");
        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        commentService.delete(1L, 1L, PostType.FREE, 1L);

        assertThat(comment.getStatus()).isEqualTo(CommentStatus.DELETED);
    }

    @Test
    void 댓글_삭제_타인_403() {
        Comment comment = Comment.create(1L, PostType.FREE, 1L, "삭제될 댓글");
        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(2L, 1L, PostType.FREE, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMENT_FORBIDDEN));
    }

    @Test
    void 이미_삭제된_댓글_삭제_400() {
        Comment comment = Comment.create(1L, PostType.FREE, 1L, "이미 삭제됨");
        comment.delete();
        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(1L, 1L, PostType.FREE, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMENT_ALREADY_DELETED));
    }
}
