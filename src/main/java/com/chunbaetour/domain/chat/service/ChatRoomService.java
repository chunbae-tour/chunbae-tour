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
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
    private final CompanionPostRepository companionPostRepository;

    @Transactional
    public CreateChatRoomResponse createRoom(Long userId, CreateChatRoomRequest request) {
        // 채팅방은 동행 게시글 작성자만 개설 가능 — 게시글 존재 확인 후 작성자 일치 여부 검증
        CompanionPost post = companionPostRepository.findById(request.postId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        ChatRoom chatRoom = ChatRoom.createWithOwner(
                request.postId(),
                userId,
                request.title(),
                request.description(),
                request.maxMembers()
        );
        // postId 유니크 제약 위반만 이 경로에서 발생 가능 — 다른 컬럼은 nullable이거나 제약 없음
        // TOCTOU 없이 DB 레벨에서 중복을 원자적으로 차단하는 팀 컨벤션
        try {
            chatRoomRepository.save(chatRoom);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_DUPLICATE);
        }

        return new CreateChatRoomResponse(chatRoom.getId());
    }

    public CursorPageResponse<MyChatRoomResponse> getMyRooms(Long userId, String cursor, int size) {
        Long cursorId = cursor != null ? decodeCursor(cursor) : Long.MAX_VALUE;
        // ACTIVE_STATES(멤버 상태)로 필터링 — close() 이후에도 멤버 상태는 OWNER_ACTIVE/MEMBER_ACTIVE 유지되므로
        // CLOSED 방은 room.status로 구분되며, 기존 멤버의 이력 조회를 위해 목록에 계속 포함됨
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

    @Transactional
    public void closeRoom(Long userId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoom.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN);
        }

        // close()는 room.status만 CLOSED로 전이 — 멤버 상태(OWNER_ACTIVE/MEMBER_ACTIVE)는 유지
        // 종료 이후에도 기존 멤버가 이전 메시지 이력을 조회할 수 있어야 하므로 의도적으로 멤버 상태를 변경하지 않음
        // 이미 CLOSED 상태면 close() 내부에서 CHAT_013 예외 발생 (트랜잭션 커밋 전 도메인 레벨 검증)
        chatRoom.close();

        // saveAndFlush로 커밋 전 DB 쓰기를 강제해 낙관적 잠금 실패를 메서드 내부에서 처리
        try {
            chatRoomRepository.saveAndFlush(chatRoom);
        } catch (ObjectOptimisticLockingFailureException e) {
            // 재조회로 실제 상태 확인 — 동시 close()가 먼저 완료됐으면 CHAT_013, 그 외 필드 충돌이면 CONCURRENT_UPDATE
            ChatRoom refreshed = chatRoomRepository.findById(roomId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
            if (refreshed.getStatus() == ChatRoomStatus.CLOSED) {
                throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
            }
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        }
    }

    @Transactional
    public void leaveRoom(Long userId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 해당 방에서 요청자의 멤버 레코드 조회 — ACTIVE/INACTIVE 관계없이 레코드 존재 여부 먼저 확인
        // 상태별 검증(OWNER_ACTIVE → CHAT_015, LEFT/KICKED → CHAT_016)은 leave() 내부에서 수행됨
        ChatRoomMember member = chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_NOT_JOINED));

        member.leave();

        // 퇴장으로 현재 인원 감소 — FULL 상태였으면 자동으로 OPEN 전환
        chatRoom.decrementMembers();
    }

    public ChatRoomDetailResponse getRoomDetail(Long userId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // ACTIVE_STATES(OWNER_ACTIVE, MEMBER_ACTIVE)만 조회 — 참여 순서(createdAt ASC) 정렬
        // KICKED/LEFT 멤버는 필터에서 제외되어 isMember 검사에서 자동으로 접근 거부 처리됨
        List<ChatRoomMember> activeMembers = chatRoomMemberRepository
                .findByChatRoomIdAndMemberStateInOrderByCreatedAtAsc(roomId, ACTIVE_STATES);

        // 비멤버, KICKED, LEFT 모두 CHAT_NOT_JOINED로 통일 — API 계약 일관성 유지
        boolean isMember = activeMembers.stream()
                .anyMatch(m -> m.getUserId().equals(userId));
        if (!isMember) {
            throw new BusinessException(ErrorCode.CHAT_NOT_JOINED);
        }

        // userId를 함께 전달해 응답에 요청자 본인의 memberState(방장 여부 포함) 포함
        return ChatRoomDetailResponse.from(chatRoom, activeMembers, userId);
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
