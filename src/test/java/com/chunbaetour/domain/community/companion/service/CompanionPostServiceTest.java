package com.chunbaetour.domain.community.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateRequest;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetListResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetOneResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostUpdateRequest;
import com.chunbaetour.domain.community.companion.dto.CompanionPostUpdateResponse;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CompanionPostServiceTest {

    @Mock CompanionPostRepository postRepository;
    @Mock AccountRepository accountRepository;
    @Mock ChatRoomRepository chatRoomRepository;
    @InjectMocks CompanionPostService postService;

    private static final Long AUTHOR_ID = 1L;
    private static final Long POST_ID   = 10L;
    private static final LocalDate MEETING_DATE = LocalDate.of(2026, 8, 1);

    private CompanionPost buildPost(Long id, CompanionPostStatus status) {
        CompanionPost post = CompanionPost.create(
                AUTHOR_ID, "제목", "내용", 100L, "장소명", "서울", MEETING_DATE, 4);
        ReflectionTestUtils.setField(post, "id", id);
        if (status == CompanionPostStatus.DELETED) post.delete();
        if (status == CompanionPostStatus.HIDDEN)  post.hide();
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
            CompanionPost p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", POST_ID);
            return p;
        });
        CompanionPostCreateRequest request = new CompanionPostCreateRequest(
                "제목", "내용", 100L, "장소명", "서울", MEETING_DATE, 4);

        CompanionPostCreateResponse response = postService.create(AUTHOR_ID, request);

        assertThat(response.postId()).isEqualTo(POST_ID);
        then(postRepository).should().save(any(CompanionPost.class));
    }

    @Test
    void create_존재하지않는_회원_USER_NOT_FOUND() {
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.create(AUTHOR_ID,
                new CompanionPostCreateRequest("제목", "내용", 100L, "장소명", "서울", MEETING_DATE, 4)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── findById ─────────────────────────────────────────────────────────

    @Test
    void findById_ACTIVE_성공() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        Account author = mockAccount(AUTHOR_ID);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        given(chatRoomRepository.findByPostId(POST_ID)).willReturn(Optional.empty());

        CompanionPostGetOneResponse response = postService.findById(POST_ID);

        assertThat(response.postId()).isEqualTo(POST_ID);
    }

    @Test
    void findById_DELETED_POST_NOT_FOUND() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.DELETED);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.findById(POST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void findById_HIDDEN_POST_NOT_FOUND() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.HIDDEN);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.findById(POST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void findById_존재하지않는_POST_NOT_FOUND() {
        given(postRepository.findById(POST_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.findById(POST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    // ── findAll ───────────────────────────────────────────────────────────

    @Test
    void findAll_hasNext_true() {
        int size = 2;
        List<CompanionPost> posts = List.of(
                buildPost(1L, CompanionPostStatus.ACTIVE),
                buildPost(2L, CompanionPostStatus.ACTIVE),
                buildPost(3L, CompanionPostStatus.ACTIVE));
        given(postRepository.findByFilters(any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(posts);
        given(accountRepository.findAllById(any())).willReturn(List.of());
        given(chatRoomRepository.findAllByPostIdIn(any())).willReturn(List.of());

        CursorPageResponse<CompanionPostGetListResponse> result = postService.findAll(null, null, null, size);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.content()).hasSize(size);
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    void findAll_hasNext_false() {
        int size = 5;
        List<CompanionPost> posts = List.of(buildPost(1L, CompanionPostStatus.ACTIVE));
        given(postRepository.findByFilters(any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(posts);
        given(accountRepository.findAllById(any())).willReturn(List.of());
        given(chatRoomRepository.findAllByPostIdIn(any())).willReturn(List.of());

        CursorPageResponse<CompanionPostGetListResponse> result = postService.findAll(null, null, null, size);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.content()).hasSize(1);
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_성공() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        Account author = mockAccount(AUTHOR_ID);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        CompanionPostUpdateRequest request = new CompanionPostUpdateRequest(
                "새제목", "새내용", null, null, null, null, null);

        CompanionPostUpdateResponse response = postService.update(AUTHOR_ID, POST_ID, request);

        assertThat(response.title()).isEqualTo("새제목");
    }

    @Test
    void update_타인_POST_UPDATE_FORBIDDEN() {
        Long otherId = 99L;
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        CompanionPostUpdateRequest request = new CompanionPostUpdateRequest(
                "새제목", null, null, null, null, null, null);

        assertThatThrownBy(() -> postService.update(otherId, POST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_UPDATE_FORBIDDEN);
    }

    @Test
    void update_placeId만_보내면_INVALID_REQUEST() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        // placeId만 있고 placeName null
        CompanionPostUpdateRequest request = new CompanionPostUpdateRequest(
                null, null, 200L, null, null, null, null);

        assertThatThrownBy(() -> postService.update(AUTHOR_ID, POST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_성공() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        postService.delete(AUTHOR_ID, POST_ID);

        assertThat(post.getStatus()).isEqualTo(CompanionPostStatus.DELETED);
    }

    @Test
    void delete_타인_POST_DELETE_FORBIDDEN() {
        Long otherId = 99L;
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.delete(otherId, POST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_DELETE_FORBIDDEN);
    }
}
