package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JoinRequestServiceTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private JoinRequestRepository joinRequestRepository;
    @Mock private AccountRepository accountRepository;

    @InjectMocks
    private JoinRequestService joinRequestService;

    private static final Long USER_ID = 2L;
    private static final Long ROOM_ID = 100L;
    private static final List<ChatMemberState> ACTIVE_STATES =
            List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);

    @Test
    void createJoinRequest_success() {
        // OPEN 방 정상 신청 — JoinRequest 저장, save() 반환 엔티티로 응답 구성
        ChatRoom room = stubOpenRoom();
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

        JoinRequest saved = mock(JoinRequest.class);
        given(saved.getId()).willReturn(1L);
        given(saved.getChatRoomId()).willReturn(ROOM_ID);
        given(saved.getUserId()).willReturn(USER_ID);
        given(saved.getMessage()).willReturn("같이 가요!");
        given(saved.getStatus()).willReturn(JoinRequestStatus.PENDING);
        given(joinRequestRepository.save(any())).willReturn(saved);

        Account account = mock(Account.class);
        given(account.getNickname()).willReturn("여행초보");
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));

        CreateJoinRequestResponse response = joinRequestService.createJoinRequest(
                USER_ID, ROOM_ID, new CreateJoinRequestRequest("같이 가요!"));

        verify(joinRequestRepository).save(any(JoinRequest.class));
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.nickname()).isEqualTo("여행초보");
    }

    @Test
    void createJoinRequest_roomNotFound_throws_CHAT_ROOM_NOT_FOUND() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> joinRequestService.createJoinRequest(
                USER_ID, ROOM_ID, new CreateJoinRequestRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void createJoinRequest_closedRoom_throws_CHAT_ROOM_CLOSED() {
        ChatRoom room = mock(ChatRoom.class);
        given(room.getStatus()).willReturn(ChatRoomStatus.CLOSED);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> joinRequestService.createJoinRequest(
                USER_ID, ROOM_ID, new CreateJoinRequestRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_ROOM_CLOSED);
    }

    @Test
    void createJoinRequest_fullRoom_throws_CHAT_ROOM_FULL() {
        ChatRoom room = mock(ChatRoom.class);
        given(room.getStatus()).willReturn(ChatRoomStatus.FULL);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> joinRequestService.createJoinRequest(
                USER_ID, ROOM_ID, new CreateJoinRequestRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_ROOM_FULL);
    }

    @Test
    void createJoinRequest_kickedUser_throws_CHAT_MEMBER_KICKED_REJOIN() {
        // 강퇴된 유저는 재참여 신청 불가 — CHAT_010
        ChatRoom room = stubOpenRoom();
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberState(
                ROOM_ID, USER_ID, ChatMemberState.MEMBER_KICKED)).willReturn(true);

        assertThatThrownBy(() -> joinRequestService.createJoinRequest(
                USER_ID, ROOM_ID, new CreateJoinRequestRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_MEMBER_KICKED_REJOIN);
    }

    @Test
    void createJoinRequest_alreadyMember_throws_ALREADY_JOINED_CHAT() {
        // OWNER_ACTIVE or MEMBER_ACTIVE 상태면 이미 참여 중 — CHAT_003
        ChatRoom room = stubOpenRoom();
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(
                ROOM_ID, USER_ID, ACTIVE_STATES)).willReturn(true);

        assertThatThrownBy(() -> joinRequestService.createJoinRequest(
                USER_ID, ROOM_ID, new CreateJoinRequestRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.ALREADY_JOINED_CHAT);
    }

    @Test
    void createJoinRequest_duplicateRequest_throws_ALREADY_APPLIED_CHAT() {
        // PENDING 신청이 이미 존재 — 중복 신청 차단 CHAT_004
        ChatRoom room = stubOpenRoom();
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(joinRequestRepository.existsByChatRoomIdAndUserIdAndStatus(
                ROOM_ID, USER_ID, JoinRequestStatus.PENDING)).willReturn(true);

        assertThatThrownBy(() -> joinRequestService.createJoinRequest(
                USER_ID, ROOM_ID, new CreateJoinRequestRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.ALREADY_APPLIED_CHAT);
    }

    private ChatRoom stubOpenRoom() {
        ChatRoom room = mock(ChatRoom.class);
        given(room.getStatus()).willReturn(ChatRoomStatus.OPEN);
        return room;
    }

    private ErrorCode extractErrorCode(Throwable ex) {
        return ((BusinessException) ex).getErrorCode();
    }
}
