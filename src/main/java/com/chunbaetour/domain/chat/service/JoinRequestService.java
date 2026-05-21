package com.chunbaetour.domain.chat.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.dto.request.CreateJoinRequestRequest;
import com.chunbaetour.domain.chat.dto.response.CreateJoinRequestResponse;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.repository.JoinRequestRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.chat.type.JoinRequestStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JoinRequestService {

    private static final List<ChatMemberState> ACTIVE_STATES =
            List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public CreateJoinRequestResponse createJoinRequest(
            Long userId, Long chatRoomId, CreateJoinRequestRequest request) {

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 종료된 방은 신청 불가
        if (chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }
        // 정원 초과 방은 신청 불가
        if (chatRoom.getStatus() == ChatRoomStatus.FULL) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_FULL);
        }

        // 강퇴 이력 확인 — 강퇴된 유저는 재참여 신청 불가 (CHAT_010)
        if (chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberState(
                chatRoomId, userId, ChatMemberState.MEMBER_KICKED)) {
            throw new BusinessException(ErrorCode.CHAT_MEMBER_KICKED_REJOIN);
        }

        // 이미 활성 멤버 확인 — OWNER_ACTIVE, MEMBER_ACTIVE 상태면 이미 참여 중 (CHAT_003)
        if (chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                chatRoomId, userId, ACTIVE_STATES)) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED_CHAT);
        }

        // 중복 신청 확인 — PENDING 상태 신청이 이미 존재하면 차단 (CHAT_004)
        if (joinRequestRepository.existsByChatRoomIdAndUserIdAndStatus(
                chatRoomId, userId, JoinRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.ALREADY_APPLIED_CHAT);
        }

        JoinRequest joinRequest = JoinRequest.builder()
                .chatRoomId(chatRoomId)
                .userId(userId)
                .message(request.message())
                .build();
        // save() 반환값 사용 — JPA가 DB 생성 ID를 채운 managed 엔티티 반환
        JoinRequest saved = joinRequestRepository.save(joinRequest);

        // 응답에 신청자 프로필 정보 포함 — 방장이 신청 목록에서 신청자를 식별할 수 있도록
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return CreateJoinRequestResponse.from(saved, account);
    }
}
