package com.chunbaetour.domain.chat.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.dto.request.CreateChatRoomRequest;
import com.chunbaetour.domain.chat.dto.response.ChatRoomDetailResponse;
import com.chunbaetour.domain.chat.dto.response.ChatRoomMemberResponse;
import com.chunbaetour.domain.chat.dto.response.CreateChatRoomResponse;
import com.chunbaetour.domain.chat.dto.response.MyChatRoomResponse;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.event.ChatMemberKickedEvent;
import com.chunbaetour.domain.chat.event.ChatOwnerTransferredEvent;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final CompanionPostRepository companionPostRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 채팅방 생성 — 동행 게시글 작성자만 개설 가능, postId 중복은 DB 제약으로 원자적 차단
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

    // 내 채팅방 목록 — ACTIVE 멤버 상태 기준 커서 페이지네이션, CLOSED 방도 포함
    public CursorPageResponse<MyChatRoomResponse> getMyRooms(Long userId, String cursor, int size) {
        Long cursorId = cursor != null ? CursorUtils.decodeSafe(cursor) : Long.MAX_VALUE;
        // 활성 멤버 상태로 필터링 — close() 이후에도 멤버 상태는 OWNER_ACTIVE/MEMBER_ACTIVE 유지되므로
        // CLOSED 방은 room.status로 구분되며, 기존 멤버의 이력 조회를 위해 목록에 계속 포함됨
        List<ChatRoomMember> members = chatRoomMemberRepository.findMyRoomsWithCursor(
                userId, ChatMemberState.activeStates(), cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = members.size() > size;
        List<ChatRoomMember> page = hasNext ? members.subList(0, size) : members;

        String nextCursor = hasNext
                ? CursorUtils.encode(page.get(page.size() - 1).getChatRoom().getId())
                : null;

        return new CursorPageResponse<>(
                page.stream().map(MyChatRoomResponse::from).toList(),
                nextCursor,
                hasNext,
                size
        );
    }

    // 채팅방 종료 — 방장만 가능, room.status만 CLOSED로 전이, 멤버 상태 유지
    @Transactional
    public void closeRoom(Long userId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoom.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN);
        }

        // close()는 room.status만 CLOSED로 전이 — 멤버 상태(OWNER_ACTIVE/MEMBER_ACTIVE)는 유지
        // 종료 이후에도 기존 멤버가 이전 메시지 이력을 조회할 수 있어야 하므로 의도적으로 멤버 상태를 변경하지 않음
        // 이미 CLOSED 상태면 close() 내부에서 CHAT_013 예외 발생 (트랜잭션 커밋 전 도메인 레벨 검증)
        chatRoom.close();
        saveClosedRoom(chatRoom, roomId);
    }

    // chatRoom.close() 후 공통 저장 — saveAndFlush로 커밋 전 DB 쓰기를 강제해 낙관적 잠금 실패를 메서드 내부에서 처리.
    // 충돌 시 재조회해 CLOSED 경쟁(동시 close 먼저 완료 → CHAT_013)과 그 외 필드 충돌(CONCURRENT_UPDATE)을 구분
    private void saveClosedRoom(ChatRoom chatRoom, Long roomId) {
        try {
            chatRoomRepository.saveAndFlush(chatRoom);
        } catch (ConcurrencyFailureException e) {
            ChatRoom refreshed = chatRoomRepository.findById(roomId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
            if (refreshed.getStatus() == ChatRoomStatus.CLOSED) {
                throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
            }
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        }
    }

    // 참여자 강퇴 — 방장만 가능, CLOSED 방 강퇴 불가, kick()으로 MEMBER_KICKED 전환 후 currentMembers -1
    @Transactional
    public void kickMember(Long ownerId, Long chatRoomId, Long targetUserId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoom.isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN);
        }

        // 종료된 방은 강퇴 불가 — 정책 미명시로 팀 협의 후 CLOSED 방 강퇴 차단으로 결정
        if (chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }

        ChatRoomMember targetMember = chatRoomMemberRepository
                .findByChatRoomIdAndUserId(chatRoomId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_NOT_JOINED));

        // kick() 내부: OWNER_ACTIVE → CHAT_017, MEMBER_LEFT/KICKED → CHAT_016
        targetMember.kick();
        chatRoomMemberRepository.saveAndFlush(targetMember);

        chatRoom.decrementMembers();

        try {
            chatRoomRepository.saveAndFlush(chatRoom);
        } catch (ConcurrencyFailureException e) {
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        }

        // 트랜잭션 커밋 후 강퇴 대상에게 알림 — AFTER_COMMIT 리스너가 REQUIRES_NEW 트랜잭션으로 저장
        eventPublisher.publishEvent(new ChatMemberKickedEvent(chatRoomId, targetUserId));
    }

    // 방장 위임 — 현재 방장만 가능, 대상은 같은 방의 MEMBER_ACTIVE만 허용. ownerId + 멤버 상태 양쪽 갱신
    @Transactional
    public void transferOwner(Long ownerId, Long roomId, Long newOwnerId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoom.isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN);
        }

        if (chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }

        // 현재 방장의 멤버 레코드가 없으면 ChatRoom.ownerId와 ChatRoomMember 상태가 불일치하는
        // 서버측 데이터 정합성 버그 — 클라이언트 요청 오류(403)가 아닌 서버 오류(500)로 처리 (demoteFromOwner()와 동일 기준)
        ChatRoomMember currentOwner = chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        // 위임 대상이 같은 방의 멤버가 아니면 CHAT_019 — 본인·강퇴·퇴장 멤버는 promoteToOwner()에서 추가 차단
        ChatRoomMember newOwner = chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, newOwnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_OWNER_TRANSFER_INVALID_TARGET));

        newOwner.promoteToOwner();
        currentOwner.demoteFromOwner();
        chatRoom.transferOwner(newOwnerId);

        // saveAndFlush로 커밋 전 DB 쓰기를 강제해 충돌을 메서드 내부에서 처리
        // ConcurrencyFailureException — @Version 낙관적 잠금 실패뿐 아니라, 동시 transferOwner/kickMember가
        // chat_rooms/chat_room_members 행을 서로 다른 순서로 잠그며 발생하는 DB 데드락(CannotAcquireLockException)도 포괄
        try {
            chatRoomRepository.saveAndFlush(chatRoom);
        } catch (ConcurrencyFailureException e) {
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        }

        // 트랜잭션 커밋 후 신규 방장에게 알림 — AFTER_COMMIT 리스너가 REQUIRES_NEW 트랜잭션으로 저장
        eventPublisher.publishEvent(new ChatOwnerTransferredEvent(roomId, newOwnerId));
    }

    // 채팅방 퇴장 — OPEN/FULL 방의 방장은 다른 ACTIVE 멤버 있으면 위임 선행(CHAT_015), 단독이면 위임 없이 방 자동 CLOSED.
    // CLOSED 방은 방장 권한이 무의미하므로 방장도 일반 멤버와 동일하게 퇴장 처리 (09_정책_결정_기록.md)
    @Transactional
    public void leaveRoom(Long userId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 해당 방에서 요청자의 멤버 레코드 조회 — ACTIVE/INACTIVE 관계없이 레코드 존재 여부 먼저 확인
        ChatRoomMember member = chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_NOT_JOINED));

        // OPEN/FULL 방의 방장 퇴장 — 다른 ACTIVE 멤버 있으면 위임 선행 필요(CHAT_015), 단독이면 위임 없이 방 자동 CLOSED
        if (member.isOwner() && chatRoom.getStatus() != ChatRoomStatus.CLOSED) {
            if (chatRoom.getCurrentMembers() > 1) {
                throw new BusinessException(ErrorCode.CHAT_OWNER_CANNOT_LEAVE);
            }

            chatRoom.close();
            saveClosedRoom(chatRoom, roomId);
            return;
        }

        // 상태별 검증(LEFT/KICKED → CHAT_016)은 leave() 내부에서 수행됨
        // CLOSED 방 방장도 이 경로로 처리 — leave()가 OWNER_ACTIVE → MEMBER_LEFT 전환을 허용
        member.leave();

        // 퇴장으로 현재 인원 감소 — FULL 상태였으면 자동으로 OPEN 전환
        chatRoom.decrementMembers();

        // saveAndFlush로 커밋 전 DB 쓰기를 강제해 낙관적 잠금 실패를 메서드 내부에서 처리
        // closeRoom과 동일한 패턴 — 트랜잭션 커밋 시 래핑 예외로 매핑 누락되는 케이스 방지
        try {
            chatRoomRepository.saveAndFlush(chatRoom);
        } catch (ConcurrencyFailureException e) {
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        }
    }

    // 채팅방 상세 조회 — ACTIVE 멤버만 접근 가능, 비멤버·강퇴·퇴장은 CHAT_NOT_JOINED
    public ChatRoomDetailResponse getRoomDetail(Long userId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 활성 멤버(OWNER_ACTIVE, MEMBER_ACTIVE)만 조회 — 참여 순서(joinedAt ASC, id ASC) 정렬
        // KICKED/LEFT 멤버는 필터에서 제외되어 isMember 검사에서 자동으로 접근 거부 처리됨
        List<ChatRoomMember> activeMembers = chatRoomMemberRepository
                .findByChatRoomIdAndMemberStateInOrderByJoinedAtAscIdAsc(roomId, ChatMemberState.activeStates());

        // 비멤버, KICKED, LEFT 모두 CHAT_NOT_JOINED로 통일 — API 계약 일관성 유지
        boolean isMember = activeMembers.stream()
                .anyMatch(m -> m.getUserId().equals(userId));
        if (!isMember) {
            throw new BusinessException(ErrorCode.CHAT_NOT_JOINED);
        }

        // userId를 함께 전달해 응답에 요청자 본인의 memberState(방장 여부 포함) 포함
        return ChatRoomDetailResponse.from(chatRoom, activeMembers, userId, fetchAccountMap(activeMembers));
    }

    // 참여자 목록 — ACTIVE 멤버만 반환, N+1 방지: userId 추출 후 Account 일괄 조회
    public List<ChatRoomMemberResponse> getMembers(Long userId, Long roomId) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        // 참여 여부 먼저 확인 — 전체 멤버 로드 전에 단건 조회로 비용 최소화
        chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
                .filter(ChatRoomMember::isActiveMember)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_NOT_JOINED));

        // 활성 멤버(OWNER_ACTIVE, MEMBER_ACTIVE)만 — KICKED/LEFT 제외, 참여 순서(joinedAt ASC, id ASC) 정렬
        List<ChatRoomMember> activeMembers = chatRoomMemberRepository
                .findByChatRoomIdAndMemberStateInOrderByJoinedAtAscIdAsc(roomId, ChatMemberState.activeStates());

        // TOCTOU 방어 — 단건 조회와 목록 조회 사이에 KICKED/LEFT로 상태 변경된 경우 재차 차단
        boolean stillActive = activeMembers.stream().anyMatch(m -> m.getUserId().equals(userId));
        if (!stillActive) {
            throw new BusinessException(ErrorCode.CHAT_NOT_JOINED);
        }

        // accountMap에 없는 userId — 탈퇴 계정, from()에서 fallback 처리
        Map<Long, Account> accountMap = fetchAccountMap(activeMembers);
        return activeMembers.stream()
                .map(m -> ChatRoomMemberResponse.from(m, accountMap.get(m.getUserId())))
                .toList();
    }

    // 멤버 userId 일괄 조회 — 개별 조회 시 N+1 발생하므로 IN 쿼리로 한 번에 로드
    private Map<Long, Account> fetchAccountMap(List<ChatRoomMember> members) {
        List<Long> userIds = members.stream().map(ChatRoomMember::getUserId).toList();
        return accountRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }
}
