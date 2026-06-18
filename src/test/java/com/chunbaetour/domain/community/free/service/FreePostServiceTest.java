package com.chunbaetour.domain.community.free.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.community.free.dto.FreePostCreateRequest;
import com.chunbaetour.domain.community.free.dto.FreePostGetListResponse;
import com.chunbaetour.domain.community.free.dto.FreePostGetOneResponse;
import com.chunbaetour.domain.community.free.dto.FreePostUpdateRequest;
import com.chunbaetour.domain.community.comment.service.CommentCountService;
import com.chunbaetour.domain.community.common.PostType;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FreePostServiceTest {

    @Mock FreePostRepository postRepository;
    @Mock AccountRepository accountRepository;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock CommentCountService commentCountService;
    @Mock PostImageService postImageService;
    @InjectMocks FreePostService postService;

    private static final Long AUTHOR_ID = 1L;
    private static final Long POST_ID   = 10L;

    private FreePost buildPost(Long id, FreePostStatus status) {
        FreePost post = FreePost.create(AUTHOR_ID, "제목", "내용", List.of());
        ReflectionTestUtils.setField(post, "id", id);
        if (status == FreePostStatus.DELETED) post.delete();
        if (status == FreePostStatus.HIDDEN)  post.hide();
        return post;
    }

    private FreePost buildPostWithImages(Long id, List<String> keys) {
        FreePost post = FreePost.create(AUTHOR_ID, "제목", "내용", keys);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private Account mockAccount(Long id) {
        Account account = org.mockito.Mockito.mock(Account.class);
        given(account.getId()).willReturn(id);
        return account;
    }

    // ── create ────────────────────────────────────────────────────────────

    @Test
    void create_성공() {
        Account author = mockAccount(AUTHOR_ID);
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        given(postRepository.save(any())).willAnswer(inv -> {
            FreePost p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", POST_ID);
            return p;
        });
        given(postImageService.presignAll(anyList())).willReturn(List.of());

        FreePostGetOneResponse response = postService.create(AUTHOR_ID,
                new FreePostCreateRequest("제목", "내용", List.of()));

        assertThat(response.postId()).isEqualTo(POST_ID);
        // IDOR 방어(키 검증)·presign 변환이 실제로 호출되는지 고정 — 제거 시 회귀 감지
        then(postImageService).should().validateOwnedKeys(eq(AUTHOR_ID), eq(List.of()));
        then(postImageService).should().presignAll(anyList());
        then(postRepository).should().save(any(FreePost.class));
    }

    @Test
    void create_존재하지않는_회원_USER_NOT_FOUND() {
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.create(AUTHOR_ID,
                new FreePostCreateRequest("제목", "내용", List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── findById ─────────────────────────────────────────────────────────

    @Test
    void findById_ACTIVE_성공() {
        FreePost post = buildPost(POST_ID, FreePostStatus.ACTIVE);
        Account author = mockAccount(AUTHOR_ID);
        given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.of(post));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        given(commentCountService.countByPost(POST_ID, PostType.FREE)).willReturn(2L);

        FreePostGetOneResponse response = postService.findById(POST_ID);

        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.viewCount()).isEqualTo(1L); // 상세 조회 시 +1
        assertThat(response.commentCount()).isEqualTo(2L);
    }

    @Test
    void findById_탈퇴한_작성자_탈퇴한사용자로_반환() {
        FreePost post = buildPost(POST_ID, FreePostStatus.ACTIVE);
        given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.of(post));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.empty());

        FreePostGetOneResponse response = postService.findById(POST_ID);

        assertThat(response.writer().nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(response.writer().userId()).isNull();
    }

    @Test
    void findById_DELETED_POST_NOT_FOUND() {
        FreePost post = buildPost(POST_ID, FreePostStatus.DELETED);
        given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.findById(POST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void findById_존재하지않는_POST_NOT_FOUND() {
        given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.findById(POST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    // ── findAll ───────────────────────────────────────────────────────────

    @Test
    void findAll_hasNext_true() {
        int size = 2;
        List<FreePost> posts = List.of(
                buildPost(1L, FreePostStatus.ACTIVE),
                buildPost(2L, FreePostStatus.ACTIVE),
                buildPost(3L, FreePostStatus.ACTIVE));
        given(postRepository.findByCursor(any(), any(), any(Pageable.class))).willReturn(posts);
        given(accountRepository.findAllById(any())).willReturn(List.of());
        given(commentCountService.countByPosts(any(), any())).willReturn(Map.of());

        CursorPageResponse<FreePostGetListResponse> result = postService.findAll(null, size);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.content()).hasSize(size);
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    void findAll_hasNext_false() {
        int size = 5;
        List<FreePost> posts = List.of(buildPost(1L, FreePostStatus.ACTIVE));
        given(postRepository.findByCursor(any(), any(), any(Pageable.class))).willReturn(posts);
        given(accountRepository.findAllById(any())).willReturn(List.of());
        given(commentCountService.countByPosts(any(), any())).willReturn(Map.of());

        CursorPageResponse<FreePostGetListResponse> result = postService.findAll(null, size);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.content()).hasSize(1);
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_성공() {
        FreePost post = buildPost(POST_ID, FreePostStatus.ACTIVE);
        Account author = mockAccount(AUTHOR_ID);
        given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.of(post));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        given(postImageService.presignAll(anyList())).willReturn(List.of());

        FreePostGetOneResponse response = postService.update(AUTHOR_ID, POST_ID,
                new FreePostUpdateRequest("새제목", "새내용", null));

        assertThat(response.title()).isEqualTo("새제목");
        // 키 검증·presign 변환 호출 고정(회귀 가드)
        then(postImageService).should().validateOwnedKeys(eq(AUTHOR_ID), any());
        then(postImageService).should().presignAll(anyList());
    }

    @Test
    void update_이미지교체_빠진_옛키_정리() {
        // 기존 [a,b] → 새 [a]로 교체 → b는 고아 → deleteAllAfterCommit([b])
        FreePost post = buildPostWithImages(POST_ID, List.of("posts/free/1/a.jpg", "posts/free/1/b.jpg"));
        Account author = mockAccount(AUTHOR_ID);
        given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.of(post));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));

        postService.update(AUTHOR_ID, POST_ID,
                new FreePostUpdateRequest(null, null, List.of("posts/free/1/a.jpg")));

        then(postImageService).should().deleteAllAfterCommit(List.of("posts/free/1/b.jpg"));
    }

    @Test
    void update_타인_POST_UPDATE_FORBIDDEN() {
        Long otherId = 99L;
        FreePost post = buildPost(POST_ID, FreePostStatus.ACTIVE);
        given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.update(otherId, POST_ID,
                new FreePostUpdateRequest("새제목", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_UPDATE_FORBIDDEN);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_성공_이미지정리() {
        FreePost post = buildPostWithImages(POST_ID, List.of("posts/free/1/a.jpg", "posts/free/1/b.jpg"));
        given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.of(post));

        postService.delete(AUTHOR_ID, POST_ID);

        assertThat(post.getStatus()).isEqualTo(FreePostStatus.DELETED);
        // 삭제된 글의 이미지 객체는 커밋 후 정리
        then(postImageService).should().deleteAllAfterCommit(
                List.of("posts/free/1/a.jpg", "posts/free/1/b.jpg"));
        then(eventPublisher).should().publishEvent(
                new com.chunbaetour.domain.community.common.event.PostDeletedEvent(
                        POST_ID, com.chunbaetour.domain.community.common.PostType.FREE));
    }

    @Test
    void delete_타인_POST_DELETE_FORBIDDEN() {
        Long otherId = 99L;
        FreePost post = buildPost(POST_ID, FreePostStatus.ACTIVE);
        given(postRepository.findByIdWithImages(POST_ID)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.delete(otherId, POST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_DELETE_FORBIDDEN);
    }
}
