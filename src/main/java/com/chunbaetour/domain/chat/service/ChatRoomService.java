package com.chunbaetour.domain.chat.service;

import com.chunbaetour.domain.chat.dto.request.CreateChatRoomRequest;
import com.chunbaetour.domain.chat.dto.response.CreateChatRoomResponse;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    public CreateChatRoomResponse createRoom(Long userId, CreateChatRoomRequest request) {
        // TODO: Post 도메인 연동 후 게시글 작성자 검증 추가
        // Post post = postRepository.findById(request.postId()).orElseThrow(() -> new BusinessException(POST_NOT_FOUND));
        // if (!post.getUserId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED);

        ChatRoom chatRoom = ChatRoom.builder()
                .postId(request.postId())
                .ownerId(userId)
                .title(request.title())
                .description(request.description())
                .maxMembers(request.maxMembers())
                .build();
        try {
            chatRoomRepository.save(chatRoom);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_DUPLICATE);
        }

        ChatRoomMember owner = ChatRoomMember.builder()
                .chatRoomId(chatRoom.getId())
                .userId(userId)
                .memberState(ChatMemberState.OWNER_ACTIVE)
                .build();
        chatRoomMemberRepository.save(owner);

        return new CreateChatRoomResponse(chatRoom.getId());
    }
}
