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
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PostQueryService postQueryService;

    @InjectMocks
    private CommentService commentService;

    private Account author;

    @BeforeEach
    void setUp() {
        author = Account.registerUser("test@example.com", "hashed", "춘배여행자");
    }

    @Test
    void 댓글_작성_성공() {
        Comment comment = Comment.create(1L, PostType.FREE, 1L, "좋은 글이에요!");
        given(accountRepository.findById(1L)).willReturn(Optional.of(author));
        given(commentRepository.save(any())).willReturn(comment);

        CommentCreateResponse response = commentService.create(1L, 1L, PostType.FREE,
                new CommentCreateRequest("좋은 글이에요!"));

        assertThat(response.content()).isEqualTo("좋은 글이에요!");
        assertThat(response.writer().nickname()).isEqualTo("춘배여행자");
    }

    @Test
    void 댓글_목록_빈_결과() {
        given(commentRepository.findByPost(1L, PostType.FREE, CommentStatus.ACTIVE, null, Pageable.ofSize(11)))
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
        given(commentRepository.findByPost(1L, PostType.FREE, CommentStatus.ACTIVE, null, Pageable.ofSize(11)))
                .willReturn(comments);
        given(accountRepository.findAllById(any())).willReturn(List.of(author));

        CursorPageResponse<CommentGetListResponse> result = commentService.findAll(1L, PostType.FREE, null, 10);

        assertThat(result.content()).hasSize(10);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    void 댓글_작성_존재하지않는_사용자_404() {
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(999L, 1L, PostType.FREE,
                new CommentCreateRequest("내용")))
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
                new CommentCreateRequest("같이 가요!"));

        assertThat(response.content()).isEqualTo("같이 가요!");
    }
}
