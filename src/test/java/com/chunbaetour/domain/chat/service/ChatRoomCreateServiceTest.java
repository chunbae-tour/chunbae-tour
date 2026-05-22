package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.chat.dto.request.CreateChatRoomRequest;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatRoomCreateServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private CompanionPostRepository companionPostRepository;

    @InjectMocks
    private ChatRoomService chatRoomService;

    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long POST_ID = 10L;

    @Test
    void createRoom_postNotFound_throws_POST_NOT_FOUND() {
        // 존재하지 않는 게시글 postId — POST_NOT_FOUND(COMMUNITY_001) 발생
        given(companionPostRepository.findById(POST_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.createRoom(AUTHOR_ID, stubRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void createRoom_notAuthor_throws_ACCESS_DENIED() {
        // 게시글 작성자가 아닌 사용자 채팅방 개설 시도 — ACCESS_DENIED(AUTH_007) 발생
        CompanionPost post = mock(CompanionPost.class);
        given(companionPostRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(post.isOwnedBy(OTHER_USER_ID)).willReturn(false);

        assertThatThrownBy(() -> chatRoomService.createRoom(OTHER_USER_ID, stubRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    private CreateChatRoomRequest stubRequest() {
        return new CreateChatRoomRequest(POST_ID, "테스트 채팅방", "설명", 5);
    }
}
