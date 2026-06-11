package com.chunbaetour.domain.community.companion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetListResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetOneResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateRequest;
import com.chunbaetour.domain.community.companion.dto.CompanionPostUpdateRequest;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import com.chunbaetour.domain.community.companion.service.CompanionPostService;
import java.time.LocalDate;
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
class CompanionPostServiceTest {

    @Mock
    private CompanionPostRepository postRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @InjectMocks
    private CompanionPostService postService;

    private Account author;
    private CompanionPost activePost;

    @BeforeEach
    void setUp() {
        author = Account.registerUser("test@example.com", "hashed", "춘배여행자");
        ReflectionTestUtils.setField(author, "id", 1L);

        activePost = CompanionPost.create(
                1L, "제주도 동행 모집", "같이 가요!", 100L, "성산일출봉",
                "제주", LocalDate.now().plusDays(7), 4);
        ReflectionTestUtils.setField(activePost, "id", 1L);
    }

    @Test
    void create_maxMembers가_2미만이면_400() {
        assertThatThrownBy(() -> postService.create(1L,
                new CompanionPostCreateRequest("제목", "내용", 1L, "장소", "서울",
                        LocalDate.now().plusDays(1), 1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void update_maxMembers가_currentMembers미만이면_400() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));

        assertThatThrownBy(() -> postService.update(1L, 1L,
                new CompanionPostUpdateRequest(null, null, null, null, null, null, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 소유자가_아닌_유저가_수정하면_403() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));

        assertThatThrownBy(() -> postService.update(999L, 1L,
                new CompanionPostUpdateRequest("수정제목", null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_UPDATE_FORBIDDEN);
    }

    @Test
    void 소유자가_아닌_유저가_삭제하면_403() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));

        assertThatThrownBy(() -> postService.delete(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_DELETE_FORBIDDEN);
    }

    @Test
    void 탈퇴한_작성자의_게시글_단건조회시_탈퇴한사용자로_반환() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));
        given(accountRepository.findById(1L)).willReturn(Optional.empty());
        given(chatRoomRepository.findByPostId(1L)).willReturn(Optional.empty());

        var result = postService.findById(1L);

        assertThat(result.writer().nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(result.writer().userId()).isNull();
    }

    @Test
    void 삭제된_게시글_조회하면_404() {
        CompanionPost deleted = CompanionPost.create(
                1L, "삭제글", "내용", 100L, "장소", "서울",
                LocalDate.now().plusDays(1), 2);
        deleted.delete();
        given(postRepository.findById(1L)).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> postService.findById(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void 커서없이_목록조회하면_전체_반환() {
        CompanionPost p1 = CompanionPost.create(1L, "글1", "내용", 1L, "장소", "서울",
                LocalDate.now().plusDays(1), 2);
        ReflectionTestUtils.setField(p1, "id", 1L);
        CompanionPost p2 = CompanionPost.create(1L, "글2", "내용", 1L, "장소", "부산",
                LocalDate.now().plusDays(2), 3);
        ReflectionTestUtils.setField(p2, "id", 2L);
        given(postRepository.findByFilters(CompanionPostStatus.ACTIVE, null, null, null, Pageable.ofSize(11)))
                .willReturn(List.of(p1, p2));
        given(accountRepository.findAllById(any())).willReturn(List.of(author));
        given(chatRoomRepository.findAllByPostIdIn(any())).willReturn(List.of());

        CursorPageResponse<CompanionPostGetListResponse> result = postService.findAll(null, null, null, 10);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void size보다_많은_결과면_hasNext_true() {
        List<CompanionPost> posts = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(i -> {
                    CompanionPost p = CompanionPost.create(1L, "글" + i, "내용", 1L, "장소", "서울",
                            LocalDate.now().plusDays(i), 2);
                    ReflectionTestUtils.setField(p, "id", (long) i);
                    return p;
                })
                .toList();
        given(postRepository.findByFilters(CompanionPostStatus.ACTIVE, null, null, null, Pageable.ofSize(11)))
                .willReturn(posts);
        given(accountRepository.findAllById(any())).willReturn(List.of(author));
        given(chatRoomRepository.findAllByPostIdIn(any())).willReturn(List.of());

        CursorPageResponse<CompanionPostGetListResponse> result = postService.findAll(null, null, null, 10);

        assertThat(result.content()).hasSize(10);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    void placeId만_있고_placeName_없으면_400() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));

        assertThatThrownBy(() -> postService.update(1L, 1L,
                new CompanionPostUpdateRequest(null, null, 200L, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void placeName만_있고_placeId_없으면_400() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));

        assertThatThrownBy(() -> postService.update(1L, 1L,
                new CompanionPostUpdateRequest(null, null, null, "새 장소명", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 단건조회_채팅방_OPEN이면_chatRoomId와_chatRoomStatus_반환() {
        ChatRoom chatRoom = ChatRoom.createWithOwner(1L, 99L, "제주 채팅방", "같이 가요", 4);
        ReflectionTestUtils.setField(chatRoom, "id", 10L);
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));
        given(accountRepository.findById(1L)).willReturn(Optional.of(author));
        given(chatRoomRepository.findByPostId(1L)).willReturn(Optional.of(chatRoom));

        CompanionPostGetOneResponse result = postService.findById(1L);

        assertThat(result.chatRoomId()).isEqualTo(10L);
        assertThat(result.chatRoomStatus()).isEqualTo(ChatRoomStatus.OPEN);
    }

    @Test
    void 단건조회_채팅방_CLOSED이면_chatRoomStatus_CLOSED_반환() {
        ChatRoom chatRoom = ChatRoom.createWithOwner(1L, 99L, "제주 채팅방", "같이 가요", 4);
        ReflectionTestUtils.setField(chatRoom, "id", 10L);
        chatRoom.close();
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));
        given(accountRepository.findById(1L)).willReturn(Optional.of(author));
        given(chatRoomRepository.findByPostId(1L)).willReturn(Optional.of(chatRoom));

        CompanionPostGetOneResponse result = postService.findById(1L);

        assertThat(result.chatRoomId()).isEqualTo(10L);
        assertThat(result.chatRoomStatus()).isEqualTo(ChatRoomStatus.CLOSED);
    }

    @Test
    void 단건조회_채팅방_없으면_chatRoomId와_chatRoomStatus_null() {
        given(postRepository.findById(1L)).willReturn(Optional.of(activePost));
        given(accountRepository.findById(1L)).willReturn(Optional.of(author));
        given(chatRoomRepository.findByPostId(1L)).willReturn(Optional.empty());

        CompanionPostGetOneResponse result = postService.findById(1L);

        assertThat(result.chatRoomId()).isNull();
        assertThat(result.chatRoomStatus()).isNull();
    }

    @Test
    void 목록조회_채팅방있는_게시글은_chatRoomId_포함() {
        CompanionPost p1 = CompanionPost.create(1L, "글1", "내용", 1L, "장소", "서울",
                LocalDate.now().plusDays(1), 2);
        ReflectionTestUtils.setField(p1, "id", 1L);
        ChatRoom chatRoom = ChatRoom.createWithOwner(1L, 99L, "서울 채팅방", null, 4);
        ReflectionTestUtils.setField(chatRoom, "id", 10L);
        given(postRepository.findByFilters(CompanionPostStatus.ACTIVE, null, null, null, Pageable.ofSize(11)))
                .willReturn(List.of(p1));
        given(accountRepository.findAllById(any())).willReturn(List.of(author));
        given(chatRoomRepository.findAllByPostIdIn(any())).willReturn(List.of(chatRoom));

        CursorPageResponse<CompanionPostGetListResponse> result = postService.findAll(null, null, null, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).chatRoomId()).isEqualTo(10L);
    }

    @Test
    void 목록조회_채팅방없는_게시글은_chatRoomId_null() {
        CompanionPost p1 = CompanionPost.create(1L, "글1", "내용", 1L, "장소", "서울",
                LocalDate.now().plusDays(1), 2);
        ReflectionTestUtils.setField(p1, "id", 1L);
        given(postRepository.findByFilters(CompanionPostStatus.ACTIVE, null, null, null, Pageable.ofSize(11)))
                .willReturn(List.of(p1));
        given(accountRepository.findAllById(any())).willReturn(List.of(author));
        given(chatRoomRepository.findAllByPostIdIn(any())).willReturn(List.of());

        CursorPageResponse<CompanionPostGetListResponse> result = postService.findAll(null, null, null, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).chatRoomId()).isNull();
    }
}
