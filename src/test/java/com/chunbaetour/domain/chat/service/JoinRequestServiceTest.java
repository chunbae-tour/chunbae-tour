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
import com.chunbaetour.domain.chat.dto.response.JoinRequestResponse;
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
import java.time.LocalDateTime;
import java.util.List;
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
    @Mock private RLock lock;

    @InjectMocks
    private JoinRequestService joinRequestService;

    private static final Long USER_ID = 2L;
    private static final Long ROOM_ID = 100L;

    @BeforeEach
    void setUp() throws InterruptedException {
        // 락 실패/인터럽트 테스트에서 override됨 — lenient 처리
        org.mockito.Mockito.lenient()
                .when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        org.mockito.Mockito.lenient()
                .when(lock.isHeldByCurrentThread()).thenReturn(true);
        // getJoinRequests 테스트에서 미사용 — lenient 처리
        org.mockito.Mockito.lenient()
                .when(redissonClient.getLock(anyString())).thenReturn(lock);

        // 락 실패 테스트에서 TransactionTemplate 미도달 — lenient 처리
        TransactionStatus txStatus = mock(TransactionStatus.class);
        org.mockito.Mockito.lenient()
                .when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(txStatus);

        // getJoinRequests 테스트에서 미사용 — lenient 처리
        org.mockito.Mockito.lenient()
                .when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
    }

    // ─── createJoinRequest ────────────────────────────────────────────────────

    @Test
    void createJoinRequest_success() {
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
    void createJoinRequest_lockFailed_throws_CONCURRENT_UPDATE() throws InterruptedException {
        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);
        given(lock.isHeldByCurrentThread()).willReturn(false);

        assertThatThrownBy(() -> joinRequestService.createJoinRequest(
                USER_ID, ROOM_ID, new CreateJoinRequestRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CONCURRENT_UPDATE);
    }

    @Test
    void createJoinRequest_interrupted_throws_CONCURRENT_UPDATE() throws InterruptedException {
        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .willThrow(new InterruptedException());
        given(lock.isHeldByCurrentThread()).willReturn(false);

        assertThatThrownBy(() -> joinRequestService.createJoinRequest(
                USER_ID, ROOM_ID, new CreateJoinRequestRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CONCURRENT_UPDATE);
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

    // ─── getJoinRequests ──────────────────────────────────────────────────────

    @Test
    void getJoinRequests_success_returnsPendingList() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(stubOpenRoom()));

        // 방장 멤버 stub
        ChatRoomMember ownerMember = mock(ChatRoomMember.class);
        given(ownerMember.getMemberState()).willReturn(ChatMemberState.OWNER_ACTIVE);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(ownerMember));

        // PENDING 신청 2건 — 신청자 2명 (batch 조회 검증)
        Long applicantId1 = 10L;
        Long applicantId2 = 11L;
        LocalDateTime now = LocalDateTime.now();

        JoinRequest req1 = mock(JoinRequest.class);
        given(req1.getId()).willReturn(1L);
        given(req1.getUserId()).willReturn(applicantId1);
        given(req1.getMessage()).willReturn("같이 가요!");
        given(req1.getStatus()).willReturn(JoinRequestStatus.PENDING);
        given(req1.getCreatedAt()).willReturn(now);

        JoinRequest req2 = mock(JoinRequest.class);
        given(req2.getId()).willReturn(2L);
        given(req2.getUserId()).willReturn(applicantId2);
        given(req2.getMessage()).willReturn("잘 부탁드려요");
        given(req2.getStatus()).willReturn(JoinRequestStatus.PENDING);
        given(req2.getCreatedAt()).willReturn(now);

        given(joinRequestRepository.findByChatRoomIdAndStatus(ROOM_ID, JoinRequestStatus.PENDING))
                .willReturn(List.of(req1, req2));

        // 신청자 2명 계정 batch 조회 stub
        Account applicant1 = mock(Account.class);
        given(applicant1.getId()).willReturn(applicantId1);
        given(applicant1.getNickname()).willReturn("여행좋아");

        Account applicant2 = mock(Account.class);
        given(applicant2.getId()).willReturn(applicantId2);
        given(applicant2.getNickname()).willReturn("동행고수");

        given(accountRepository.findAllById(List.of(applicantId1, applicantId2)))
                .willReturn(List.of(applicant1, applicant2));

        List<JoinRequestResponse> result = joinRequestService.getJoinRequests(USER_ID, ROOM_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).applicant().userId()).isEqualTo(applicantId1);
        assertThat(result.get(0).applicant().nickname()).isEqualTo("여행좋아");
        assertThat(result.get(0).createdAt()).isEqualTo(now);
        assertThat(result.get(1).applicant().userId()).isEqualTo(applicantId2);
        assertThat(result.get(1).message()).isEqualTo("잘 부탁드려요");
    }

    @Test
    void getJoinRequests_roomNotFound_throws_CHAT_ROOM_NOT_FOUND() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> joinRequestService.getJoinRequests(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void getJoinRequests_notOwner_throws_CHAT_SETTING_FORBIDDEN() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(stubOpenRoom()));

        // 방장이 아닌 일반 멤버
        ChatRoomMember regularMember = mock(ChatRoomMember.class);
        given(regularMember.getMemberState()).willReturn(ChatMemberState.MEMBER_ACTIVE);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(regularMember));

        assertThatThrownBy(() -> joinRequestService.getJoinRequests(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN);
    }

    @Test
    void getJoinRequests_notMember_throws_CHAT_SETTING_FORBIDDEN() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(stubOpenRoom()));

        // 채팅방 멤버 아님
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> joinRequestService.getJoinRequests(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN);
    }

    @Test
    void getJoinRequests_emptyList_returnsEmpty() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(stubOpenRoom()));

        ChatRoomMember ownerMember = mock(ChatRoomMember.class);
        given(ownerMember.getMemberState()).willReturn(ChatMemberState.OWNER_ACTIVE);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(ownerMember));

        given(joinRequestRepository.findByChatRoomIdAndStatus(ROOM_ID, JoinRequestStatus.PENDING))
                .willReturn(List.of());
        // requests 빈 리스트 → userIds 빈 리스트 → findAllById(빈 리스트) 호출됨
        given(accountRepository.findAllById(List.of())).willReturn(List.of());

        List<JoinRequestResponse> result = joinRequestService.getJoinRequests(USER_ID, ROOM_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getJoinRequests_deletedApplicant_returnsWithdrawUser() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(stubOpenRoom()));

        ChatRoomMember ownerMember = mock(ChatRoomMember.class);
        given(ownerMember.getMemberState()).willReturn(ChatMemberState.OWNER_ACTIVE);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(ownerMember));

        Long deletedUserId = 99L;
        LocalDateTime now = LocalDateTime.now();
        JoinRequest req = mock(JoinRequest.class);
        given(req.getId()).willReturn(1L);
        given(req.getUserId()).willReturn(deletedUserId);
        given(req.getStatus()).willReturn(JoinRequestStatus.PENDING);
        given(req.getCreatedAt()).willReturn(now);

        given(joinRequestRepository.findByChatRoomIdAndStatus(ROOM_ID, JoinRequestStatus.PENDING))
                .willReturn(List.of(req));
        // 탈퇴 계정 — findAllById 결과에 포함되지 않음 → accountMap.get() = null
        given(accountRepository.findAllById(List.of(deletedUserId))).willReturn(List.of());

        List<JoinRequestResponse> result = joinRequestService.getJoinRequests(USER_ID, ROOM_ID);

        assertThat(result).hasSize(1);
        // WriterInfo.from(null) → userId=null, nickname="탈퇴한 사용자"
        assertThat(result.get(0).applicant().userId()).isNull();
        assertThat(result.get(0).applicant().nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(result.get(0).createdAt()).isEqualTo(now);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private ChatRoom stubOpenRoom() {
        return mock(ChatRoom.class);
    }

    private ErrorCode extractErrorCode(Throwable ex) {
        return ((BusinessException) ex).getErrorCode();
    }
}
