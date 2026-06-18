package com.chunbaetour.domain.community.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateRequest;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetListResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetOneResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostUpdateRequest;
import com.chunbaetour.domain.community.companion.dto.CompanionPostUpdateResponse;
import com.chunbaetour.domain.community.comment.service.CommentCountService;
import com.chunbaetour.domain.community.common.PostType;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.entity.CompanionTargetType;
import com.chunbaetour.domain.community.companion.repository.CompanionPostQueryRepository;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CompanionPostServiceTest {

    @Mock CompanionPostRepository postRepository;
    @Mock CompanionPostQueryRepository postQueryRepository;
    @Mock AccountRepository accountRepository;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock CommentCountService commentCountService;
    @Mock CompanionTargetValidator targetValidator;
    @InjectMocks CompanionPostService postService;

    private static final Long AUTHOR_ID = 1L;
    private static final Long POST_ID   = 10L;
    private static final LocalDate MEETING_DATE = LocalDate.of(2026, 8, 1);

    private CompanionPost buildPost(Long id, CompanionPostStatus status) {
        CompanionPost post = CompanionPost.create(
                AUTHOR_ID, "제목", "내용", CompanionTargetType.PLACE, 100L, "장소명", "서울", MEETING_DATE, 4);
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
                "제목", "내용", CompanionTargetType.PLACE, 100L, "장소명", "서울", MEETING_DATE, 4);

        CompanionPostCreateResponse response = postService.create(AUTHOR_ID, request);

        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.targetType()).isEqualTo(CompanionTargetType.PLACE);
        assertThat(response.targetId()).isEqualTo(100L);
        then(targetValidator).should().validateExists(CompanionTargetType.PLACE, 100L);
        then(postRepository).should().save(any(CompanionPost.class));
    }

    @Test
    void create_존재하지않는_대상_검증실패_전파() {
        // 검증 실패로 응답을 만들지 않으므로 getId 스텁 불필요 — 평범한 mock 사용
        Account author = org.mockito.Mockito.mock(Account.class);
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        org.mockito.BDDMockito.willThrow(new BusinessException(ErrorCode.FESTIVAL_NOT_FOUND))
                .given(targetValidator).validateExists(CompanionTargetType.FESTIVAL, 777L);
        CompanionPostCreateRequest request = new CompanionPostCreateRequest(
                "제목", "내용", CompanionTargetType.FESTIVAL, 777L, "축제명", "서울", MEETING_DATE, 4);

        assertThatThrownBy(() -> postService.create(AUTHOR_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FESTIVAL_NOT_FOUND);
        then(postRepository).should(org.mockito.Mockito.never()).save(any());
    }

    @Test
    void create_존재하지않는_회원_USER_NOT_FOUND() {
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.create(AUTHOR_ID,
                new CompanionPostCreateRequest("제목", "내용", CompanionTargetType.PLACE, 100L, "장소명", "서울", MEETING_DATE, 4)))
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
        given(commentCountService.countByPost(POST_ID, PostType.COMPANION)).willReturn(2L);

        CompanionPostGetOneResponse response = postService.findById(POST_ID);

        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.viewCount()).isEqualTo(1L); // 상세 조회 시 +1
        assertThat(response.commentCount()).isEqualTo(2L);
    }

    @Test
    void findById_탈퇴한_작성자_탈퇴한사용자로_반환() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.empty());
        given(chatRoomRepository.findByPostId(POST_ID)).willReturn(Optional.empty());

        CompanionPostGetOneResponse response = postService.findById(POST_ID);

        assertThat(response.writer().nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(response.writer().userId()).isNull();
    }

    @Test
    void findById_채팅방_OPEN_chatRoomId_반환() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        Account author = mockAccount(AUTHOR_ID);
        ChatRoom chatRoom = ChatRoom.createWithOwner(POST_ID, 99L, "채팅방", null, 4);
        ReflectionTestUtils.setField(chatRoom, "id", 10L);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        given(chatRoomRepository.findByPostId(POST_ID)).willReturn(Optional.of(chatRoom));

        CompanionPostGetOneResponse response = postService.findById(POST_ID);

        assertThat(response.chatRoomId()).isEqualTo(10L);
        assertThat(response.chatRoomStatus()).isEqualTo(ChatRoomStatus.OPEN);
    }

    @Test
    void findById_채팅방_CLOSED_chatRoomStatus_CLOSED() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        Account author = mockAccount(AUTHOR_ID);
        ChatRoom chatRoom = ChatRoom.createWithOwner(POST_ID, 99L, "채팅방", null, 4);
        ReflectionTestUtils.setField(chatRoom, "id", 10L);
        chatRoom.close();
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        given(chatRoomRepository.findByPostId(POST_ID)).willReturn(Optional.of(chatRoom));

        CompanionPostGetOneResponse response = postService.findById(POST_ID);

        assertThat(response.chatRoomId()).isEqualTo(10L);
        assertThat(response.chatRoomStatus()).isEqualTo(ChatRoomStatus.CLOSED);
    }

    @Test
    void findById_채팅방_없으면_chatRoomId_null() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        Account author = mockAccount(AUTHOR_ID);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        given(chatRoomRepository.findByPostId(POST_ID)).willReturn(Optional.empty());

        CompanionPostGetOneResponse response = postService.findById(POST_ID);

        assertThat(response.chatRoomId()).isNull();
        assertThat(response.chatRoomStatus()).isNull();
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
        given(postQueryRepository.findByFilters(any(), any(), any(), any(), anyInt()))
                .willReturn(posts);
        given(accountRepository.findAllById(any())).willReturn(List.of());
        given(commentCountService.countByPosts(any(), any())).willReturn(Map.of());
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
        given(postQueryRepository.findByFilters(any(), any(), any(), any(), anyInt()))
                .willReturn(posts);
        given(accountRepository.findAllById(any())).willReturn(List.of());
        given(commentCountService.countByPosts(any(), any())).willReturn(Map.of());
        given(chatRoomRepository.findAllByPostIdIn(any())).willReturn(List.of());

        CursorPageResponse<CompanionPostGetListResponse> result = postService.findAll(null, null, null, size);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.content()).hasSize(1);
    }

    @Test
    void findAll_채팅방있는_게시글_chatRoomId_포함() {
        CompanionPost post = buildPost(1L, CompanionPostStatus.ACTIVE);
        ChatRoom chatRoom = ChatRoom.createWithOwner(1L, 99L, "채팅방", null, 4);
        ReflectionTestUtils.setField(chatRoom, "id", 10L);
        given(postQueryRepository.findByFilters(any(), any(), any(), any(), anyInt()))
                .willReturn(List.of(post));
        given(accountRepository.findAllById(any())).willReturn(List.of());
        given(commentCountService.countByPosts(any(), any())).willReturn(Map.of());
        given(chatRoomRepository.findAllByPostIdIn(any())).willReturn(List.of(chatRoom));

        CursorPageResponse<CompanionPostGetListResponse> result = postService.findAll(null, null, null, 5);

        assertThat(result.content().get(0).chatRoomId()).isEqualTo(10L);
    }

    @Test
    void findAll_채팅방없는_게시글_chatRoomId_null() {
        CompanionPost post = buildPost(1L, CompanionPostStatus.ACTIVE);
        given(postQueryRepository.findByFilters(any(), any(), any(), any(), anyInt()))
                .willReturn(List.of(post));
        given(accountRepository.findAllById(any())).willReturn(List.of());
        given(commentCountService.countByPosts(any(), any())).willReturn(Map.of());
        given(chatRoomRepository.findAllByPostIdIn(any())).willReturn(List.of());

        CursorPageResponse<CompanionPostGetListResponse> result = postService.findAll(null, null, null, 5);

        assertThat(result.content().get(0).chatRoomId()).isNull();
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_성공() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        Account author = mockAccount(AUTHOR_ID);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        CompanionPostUpdateRequest request = new CompanionPostUpdateRequest(
                "새제목", "새내용", null, null, null, null, null, null);

        CompanionPostUpdateResponse response = postService.update(AUTHOR_ID, POST_ID, request);

        assertThat(response.title()).isEqualTo("새제목");
    }

    @Test
    void update_대상_3종_변경시_실존검증_호출() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        Account author = mockAccount(AUTHOR_ID);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(accountRepository.findById(AUTHOR_ID)).willReturn(Optional.of(author));
        CompanionPostUpdateRequest request = new CompanionPostUpdateRequest(
                null, null, CompanionTargetType.MARKET, 55L, "새시장", null, null, null);

        CompanionPostUpdateResponse response = postService.update(AUTHOR_ID, POST_ID, request);

        then(targetValidator).should().validateExists(CompanionTargetType.MARKET, 55L);
        assertThat(response.targetType()).isEqualTo(CompanionTargetType.MARKET);
        assertThat(response.targetId()).isEqualTo(55L);
    }

    @Test
    void update_타인_POST_UPDATE_FORBIDDEN() {
        Long otherId = 99L;
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        CompanionPostUpdateRequest request = new CompanionPostUpdateRequest(
                "새제목", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> postService.update(otherId, POST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_UPDATE_FORBIDDEN);
    }

    @Test
    void update_targetName만_있고_나머지_없으면_INVALID_REQUEST() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.update(AUTHOR_ID, POST_ID,
                new CompanionPostUpdateRequest(null, null, null, null, "새 장소명", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void update_targetId만_보내면_INVALID_REQUEST() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        // targetId만 있고 targetType/targetName null → 부분 입력
        CompanionPostUpdateRequest request = new CompanionPostUpdateRequest(
                null, null, null, 200L, null, null, null, null);

        assertThatThrownBy(() -> postService.update(AUTHOR_ID, POST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void update_maxMembers가_currentMembers미만이면_INVALID_INPUT_VALUE() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        ReflectionTestUtils.setField(post, "currentMembers", 3);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        // DTO 제약(@Min(2))은 통과하지만 현재 인원(3)보다 작음 — 서비스 규칙만 단독 검증
        CompanionPostUpdateRequest request = new CompanionPostUpdateRequest(
                null, null, null, null, null, null, null, 2);

        assertThatThrownBy(() -> postService.update(AUTHOR_ID, POST_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_성공() {
        CompanionPost post = buildPost(POST_ID, CompanionPostStatus.ACTIVE);
        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));

        postService.delete(AUTHOR_ID, POST_ID);

        assertThat(post.getStatus()).isEqualTo(CompanionPostStatus.DELETED);
        then(eventPublisher).should().publishEvent(
                new com.chunbaetour.domain.community.common.event.PostDeletedEvent(
                        POST_ID, com.chunbaetour.domain.community.common.PostType.COMPANION));
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
