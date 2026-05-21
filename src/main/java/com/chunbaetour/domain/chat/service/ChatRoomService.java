package com.chunbaetour.domain.chat.service;

import com.chunbaetour.domain.chat.dto.request.CreateChatRoomRequest;
import com.chunbaetour.domain.chat.dto.response.ChatRoomDetailResponse;
import com.chunbaetour.domain.chat.dto.response.CreateChatRoomResponse;
import com.chunbaetour.domain.chat.dto.response.MyChatRoomResponse;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private static final List<ChatMemberState> ACTIVE_STATES =
            List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Transactional
    public CreateChatRoomResponse createRoom(Long userId, CreateChatRoomRequest request) {
        // TODO: Post 도메인 연동 후 게시글 작성자 검증 추가
        // Post post = postRepository.findById(request.postId()).orElseThrow(() -> new BusinessException(POST_NOT_FOUND));
        // if (!post.getUserId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED);

        ChatRoom chatRoom = ChatRoom.createWithOwner(
                request.postId(),
                userId,
                request.title(),
                request.description(),
                request.maxMembers()
        );
        try {
            chatRoomRepository.save(chatRoom);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_DUPLICATE);
        }

        return new CreateChatRoomResponse(chatRoom.getId());
    }

    public CursorPageResponse<MyChatRoomResponse> getMyRooms(Long userId, String cursor, int size) {
        Long cursorId = cursor != null ? decodeCursor(cursor) : Long.MAX_VALUE;
        List<ChatRoomMember> members = chatRoomMemberRepository.findMyRoomsWithCursor(
                userId, ACTIVE_STATES, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = members.size() > size;
        List<ChatRoomMember> page = hasNext ? members.subList(0, size) : members;

        String nextCursor = hasNext
                ? encodeCursor(page.get(page.size() - 1).getChatRoom().getId())
                : null;

        return new CursorPageResponse<>(
                page.stream().map(MyChatRoomResponse::from).toList(),
                nextCursor,
                hasNext,
                size
        );
    }

    public ChatRoomDetailResponse getRoomDetail(Long userId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // ACTIVE_STATES(OWNER_ACTIVE, MEMBER_ACTIVE)만 조회 — KICKED/LEFT 멤버는 목록에서 제외되어
        // isMember 검사에서 자동으로 접근 거부 처리됨
        List<ChatRoomMember> activeMembers = chatRoomMemberRepository
                .findByChatRoomIdAndMemberStateIn(roomId, ACTIVE_STATES);

        // 비멤버, KICKED, LEFT 모두 CHAT_NOT_JOINED로 통일 — API 계약 일관성 유지
        boolean isMember = activeMembers.stream()
                .anyMatch(m -> m.getUserId().equals(userId));
        if (!isMember) {
            throw new BusinessException(ErrorCode.CHAT_NOT_JOINED);
        }

        return ChatRoomDetailResponse.from(chatRoom, activeMembers);
    }

    // cursor는 "채팅방 ID를 URL-safe Base64로 인코딩한 문자열"
    // URL-safe 디코더 사용 — padding 없는 형태(withoutPadding)로 인코딩하므로 표준 디코더와 호환
    private Long decodeCursor(String cursor) {
        try {
            long id = Long.parseLong(
                    new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            // IDENTITY PK는 1 이상 — 0이나 음수는 조작된 커서로 판단
            if (id <= 0) throw new IllegalArgumentException();
            return id;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }

    private String encodeCursor(Long id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(id).getBytes(StandardCharsets.UTF_8));
    }
}
