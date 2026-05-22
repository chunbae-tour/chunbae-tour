package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.dto.request.CreateJoinRequestRequest;
import com.chunbaetour.domain.chat.dto.response.CreateJoinRequestResponse;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.repository.JoinRequestRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.chat.type.JoinRequestStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class JoinRequestServiceTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private JoinRequestRepository joinRequestRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private RedissonClient redissonClient;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private Account account;

    @InjectMocks
    private JoinRequestService joinRequestService;

    private static final Long USER_ID = 2L;
    private static final Long ROOM_ID = 100L;

    @BeforeEach
    void setUp() throws InterruptedException {
        // RedissonClient 분산 락 목 — 락 획득 성공, 현재 스레드 보유 상태
        RLock lock = mock(RLock.class);
        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(redissonClient.getLock(anyString())).willReturn(lock);

        // TransactionTemplate.execute()가 콜백을 실제로 실행하도록 TransactionStatus 목 설정
        TransactionStatus txStatus = mock(TransactionStatus.class);
        given(transactionManager.getTransaction(any(TransactionDefinition.class))).willReturn(txStatus);

        // Account 조회는 락 밖에서 먼저 실행 — 모든 테스트에서 USER_ID 기준 스텁 필요
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
    }

    @Test
    void createJoinRequest_success() {
        // OPEN 방 정상 신청 — JoinRequest 저장, save() 반환 엔티티로 응답 구성
        given(account.getId()).willReturn(USER_ID);
        given(account.getNickname()).willReturn("여행초보");

        ChatRoom room = stubOpenRoom();
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

        JoinRequest saved = mock(JoinRequest.class);
        given(saved.getId()).willReturn(1L);
        given(saved.getChatRoomId()).willReturn(ROOM_ID);
        given(saved.getMessage()).willReturn("같이 가요!");
        given(saved.getStatus()).willReturn(JoinRequestStatus.PENDING);
        given(joinRequestRepository.save(any())).willReturn(saved);

        CreateJoinRequestResponse response = joinRequestService.createJoinRequest(
                USER_ID, ROOM_ID, new CreateJoinRequestRequest("같이 가요!"));

        verify(joinRequestRepository).save(any(JoinRequest.class));
        assertThat(response.writer().userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(response.writer().nickname()).isEqualTo("여행초보");
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
        doThrow(new BusinessException(ErrorCode.CHAT_ROOM_CLOSED)).when(room).validateJoinable();
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
        doThrow(new BusinessException(ErrorCode.CHAT_ROOM_FULL)).when(room).validateJoinable();
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

        ChatRoomMember kickedMember = mock(ChatRoomMember.class);
        given(kickedMember.getMemberState()).willReturn(ChatMemberState.MEMBER_KICKED);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(kickedMember));

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

        ChatRoomMember activeMember = mock(ChatRoomMember.class);
        given(activeMember.getMemberState()).willReturn(ChatMemberState.MEMBER_ACTIVE);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(activeMember));

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
        return mock(ChatRoom.class);
    }

    private ErrorCode extractErrorCode(Throwable ex) {
        return ((BusinessException) ex).getErrorCode();
    }
}
