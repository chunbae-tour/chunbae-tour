package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.dto.request.CreateJoinRequestRequest;
import com.chunbaetour.domain.chat.dto.response.ApproveJoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.CreateJoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.JoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.RejectJoinRequestResponse;
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
    private static final Long OWNER_ID = 1L;
    private static final Long ROOM_ID = 100L;
    private static final Long REQUEST_ID = 50L;

    @BeforeEach
    void setUp() throws InterruptedException {
        // 락 실패/인터럽트 테스트에서 override됨 — lenient 처리
        lenient()
                .when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient()
                .when(lock.isHeldByCurrentThread()).thenReturn(true);
        lenient()
                .when(redissonClient.getLock(anyString())).thenReturn(lock);

        // 락 실패 테스트에서 TransactionTemplate 미도달 — lenient 처리
        TransactionStatus txStatus = mock(TransactionStatus.class);
        lenient()
                .when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(txStatus);

        // approveJoinRequest 테스트에서 미사용 — lenient 처리
        lenient()
                .when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
    }

    // ─── createJoinRequest ────────────────────────────────────────────────────

    @Test
    void createJoinRequest_success() {
        // 정상 신청 — JoinRequest 저장 및 writer 정보 반환 검증
        given(account.getId()).willReturn(USER_ID);
        given(account.getNickname()).willReturn("여행초보");

        ChatRoom room = mock(ChatRoom.class);
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
        // 분산 락 획득 실패 — tryLock false 시 CONCURRENT_UPDATE
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
        // 락 대기 중 인터럽트 — InterruptedException → CONCURRENT_UPDATE, 스레드 상태 복원
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
        // 존재하지 않는 방 신청 — CHAT_ROOM_NOT_FOUND
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> joinRequestService.createJoinRequest(
                USER_ID, ROOM_ID, new CreateJoinRequestRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void createJoinRequest_closedRoom_throws_CHAT_ROOM_CLOSED() {
        // CLOSED 방 신청 — validateJoinable()에서 CHAT_ROOM_CLOSED
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
        // 정원 초과 방 신청 — validateJoinable()에서 CHAT_ROOM_FULL
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
        // 강퇴 이력 있는 사용자 재신청 — CHAT_MEMBER_KICKED_REJOIN
        ChatRoom room = mock(ChatRoom.class);
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
        // 이미 활성 참여 중인 사용자 — ALREADY_JOINED_CHAT
        ChatRoom room = mock(ChatRoom.class);
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
        // PENDING 신청 중복 — ALREADY_APPLIED_CHAT
        ChatRoom room = mock(ChatRoom.class);
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
        // 방장의 PENDING 신청 목록 조회 — 신청자 batch 조회 및 매핑 검증
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);

        // 방장 멤버 stub
        ChatRoomMember ownerMember = mock(ChatRoomMember.class);
        given(ownerMember.isOwner()).willReturn(true);
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

        given(joinRequestRepository.findByChatRoomIdAndStatusOrderByCreatedAtAsc(ROOM_ID, JoinRequestStatus.PENDING))
                .willReturn(List.of(req1, req2));

        // 신청자 2명 계정 batch 조회 stub
        Account applicant1 = mock(Account.class);
        given(applicant1.getId()).willReturn(applicantId1);
        given(applicant1.getNickname()).willReturn("여행좋아");

        Account applicant2 = mock(Account.class);
        given(applicant2.getId()).willReturn(applicantId2);
        given(applicant2.getNickname()).willReturn("동행고수");

        given(accountRepository.findAllById(anyList()))
                .willReturn(List.of(applicant1, applicant2));

        List<JoinRequestResponse> result = joinRequestService.getJoinRequests(USER_ID, ROOM_ID);

        verify(accountRepository).findAllById(List.of(applicantId1, applicantId2));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).applicant().userId()).isEqualTo(applicantId1);
        assertThat(result.get(0).applicant().nickname()).isEqualTo("여행좋아");
        assertThat(result.get(0).createdAt()).isEqualTo(now);
        assertThat(result.get(1).applicant().userId()).isEqualTo(applicantId2);
        assertThat(result.get(1).applicant().nickname()).isEqualTo("동행고수");
        assertThat(result.get(1).message()).isEqualTo("잘 부탁드려요");
    }

    @Test
    void getJoinRequests_roomNotFound_throws_CHAT_ROOM_NOT_FOUND() {
        // 존재하지 않는 방 조회 — CHAT_ROOM_NOT_FOUND
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(false);

        assertThatThrownBy(() -> joinRequestService.getJoinRequests(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void getJoinRequests_notOwner_throws_CHAT_SETTING_FORBIDDEN() {
        // 방장이 아닌 일반 멤버 조회 시도 — CHAT_SETTING_FORBIDDEN
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);

        // 방장이 아닌 일반 멤버 — isOwner() false 기본값
        ChatRoomMember regularMember = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(regularMember));

        assertThatThrownBy(() -> joinRequestService.getJoinRequests(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN);
    }

    @Test
    void getJoinRequests_notMember_throws_CHAT_SETTING_FORBIDDEN() {
        // 채팅방 비멤버 조회 시도 — CHAT_SETTING_FORBIDDEN
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);

        // 채팅방 멤버 아님
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> joinRequestService.getJoinRequests(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN);
    }

    @Test
    void getJoinRequests_emptyList_returnsEmpty() {
        // 신청 없는 방 — 빈 리스트 반환, findAllById 빈 리스트 호출
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);

        ChatRoomMember ownerMember = mock(ChatRoomMember.class);
        given(ownerMember.isOwner()).willReturn(true);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(ownerMember));

        given(joinRequestRepository.findByChatRoomIdAndStatusOrderByCreatedAtAsc(ROOM_ID, JoinRequestStatus.PENDING))
                .willReturn(List.of());
        // requests 빈 리스트 → userIds 빈 리스트 → findAllById(빈 리스트) 호출됨
        given(accountRepository.findAllById(List.of())).willReturn(List.of());

        List<JoinRequestResponse> result = joinRequestService.getJoinRequests(USER_ID, ROOM_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getJoinRequests_deletedApplicant_returnsWithdrawUser() {
        // 탈퇴 계정 신청자 — accountMap에 없으면 "탈퇴한 사용자" 반환
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);

        ChatRoomMember ownerMember = mock(ChatRoomMember.class);
        given(ownerMember.isOwner()).willReturn(true);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(ownerMember));

        Long deletedUserId = 99L;
        LocalDateTime now = LocalDateTime.now();
        JoinRequest req = mock(JoinRequest.class);
        given(req.getId()).willReturn(1L);
        given(req.getUserId()).willReturn(deletedUserId);
        given(req.getStatus()).willReturn(JoinRequestStatus.PENDING);
        given(req.getCreatedAt()).willReturn(now);

        given(joinRequestRepository.findByChatRoomIdAndStatusOrderByCreatedAtAsc(ROOM_ID, JoinRequestStatus.PENDING))
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

    // ─── approveJoinRequest ───────────────────────────────────────────────────

    @Test
    void approveJoinRequest_success() {
        // 정상 수락 — 신규 멤버 생성, currentMembers +1 검증
        JoinRequest req = stubPendingRequest();
        // outer 선행 체크용 (락 전) — 검증 통과
        given(joinRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(req));
        // inner doApproveJoinRequest용 SELECT FOR UPDATE — approve↔reject 직렬화
        given(joinRequestRepository.findByIdWithLock(REQUEST_ID)).willReturn(Optional.of(req));

        ChatRoomMember ownerMember = stubOwnerMember();
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(ownerMember));

        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(ROOM_ID);
        given(chatRoomRepository.findByIdWithLock(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(chatRoom.getCurrentMembers()).willReturn(3);
        given(req.getStatus()).willReturn(JoinRequestStatus.APPROVED);
        // 신규 참여 케이스 — LEFT 이력 없음
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.empty());

        ApproveJoinRequestResponse response = joinRequestService.approveJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID);

        verify(req).approve();
        verify(chatRoom).incrementMembers();
        verify(chatRoomMemberRepository).save(any(ChatRoomMember.class));
        assertThat(response.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(response.chatRoomId()).isEqualTo(ROOM_ID);
        assertThat(response.currentMembers()).isEqualTo(3);
    }

    @Test
    void approveJoinRequest_leftMemberRejoin_reactivates() {
        // LEFT 이력 멤버 재수락 — save 대신 reactivate() 호출
        JoinRequest req = stubPendingRequest();
        given(joinRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(req));
        given(joinRequestRepository.findByIdWithLock(REQUEST_ID)).willReturn(Optional.of(req));

        ChatRoomMember ownerMember = stubOwnerMember();
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(ownerMember));

        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(ROOM_ID);
        given(chatRoomRepository.findByIdWithLock(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(chatRoom.getCurrentMembers()).willReturn(3);
        given(req.getStatus()).willReturn(JoinRequestStatus.APPROVED);

        // LEFT 상태 기존 멤버 레코드 존재 — reactivate 분기
        ChatRoomMember leftMember = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(leftMember));

        joinRequestService.approveJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID);

        verify(leftMember).reactivate();
        verify(chatRoomMemberRepository, never()).save(any(ChatRoomMember.class));
    }

    @Test
    void approveJoinRequest_wrongChatRoomId_throws_CHAT_APPLICATION_NOT_FOUND() {
        // 경로 chatRoomId와 신청 chatRoomId 불일치 — CHAT_APPLICATION_NOT_FOUND
        JoinRequest req = stubPendingRequest(); // getChatRoomId() = ROOM_ID
        given(joinRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(req));

        assertThatThrownBy(() -> joinRequestService.approveJoinRequest(OWNER_ID, 999L, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_APPLICATION_NOT_FOUND);
    }

    @Test
    void approveJoinRequest_requestNotFound_throws_CHAT_APPLICATION_NOT_FOUND() {
        // 존재하지 않는 신청 수락 — CHAT_APPLICATION_NOT_FOUND
        given(joinRequestRepository.findById(REQUEST_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> joinRequestService.approveJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_APPLICATION_NOT_FOUND);
    }

    @Test
    void approveJoinRequest_notOwner_throws_CHAT_SETTING_FORBIDDEN() {
        // 방장이 아닌 멤버 수락 시도 — CHAT_SETTING_FORBIDDEN
        JoinRequest req = stubPendingRequest();
        given(joinRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(req));

        // OWNER_ID가 MEMBER_ACTIVE 상태 — isOwner() false 기본값
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(member));

        assertThatThrownBy(() -> joinRequestService.approveJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN);
    }

    @Test
    void approveJoinRequest_alreadyProcessed_throws_CHAT_APPLICATION_ALREADY_PROCESSED() {
        // 이미 처리된 신청 수락 — approve()에서 CHAT_APPLICATION_ALREADY_PROCESSED
        JoinRequest req = stubPendingRequest();
        given(joinRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(req));
        given(joinRequestRepository.findByIdWithLock(REQUEST_ID)).willReturn(Optional.of(req));

        ChatRoomMember ownerMember = stubOwnerMember();
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(ownerMember));

        // approve()가 이미 처리된 신청에 대해 예외 발생 (CHAT_012)
        doThrow(new BusinessException(ErrorCode.CHAT_APPLICATION_ALREADY_PROCESSED))
                .when(req).approve();

        assertThatThrownBy(() -> joinRequestService.approveJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_APPLICATION_ALREADY_PROCESSED);
    }

    @Test
    void approveJoinRequest_lockFailed_throws_CONCURRENT_UPDATE() throws InterruptedException {
        // 분산 락 획득 실패 — tryLock false 시 CONCURRENT_UPDATE
        JoinRequest req = stubPendingRequest();
        given(joinRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(req));

        ChatRoomMember ownerMember = stubOwnerMember();
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(ownerMember));

        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);
        given(lock.isHeldByCurrentThread()).willReturn(false);

        assertThatThrownBy(() -> joinRequestService.approveJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CONCURRENT_UPDATE);
    }

    // ─── cancelJoinRequest ───────────────────────────────────────────────────

    @Test
    void cancelJoinRequest_success() {
        // 정상 취소 — deleteIfPending 영향 행 1 반환, 신청 삭제 확인
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        JoinRequest req = mock(JoinRequest.class);
        given(req.getChatRoomId()).willReturn(ROOM_ID);
        given(req.getUserId()).willReturn(USER_ID);
        given(joinRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(req));
        given(joinRequestRepository.deleteIfPending(REQUEST_ID)).willReturn(1);

        joinRequestService.cancelJoinRequest(USER_ID, ROOM_ID, REQUEST_ID);

        verify(joinRequestRepository).deleteIfPending(REQUEST_ID);
        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    void cancelJoinRequest_roomNotFound_throws_CHAT_ROOM_NOT_FOUND() {
        // 존재하지 않는 채팅방 — CHAT_ROOM_NOT_FOUND
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(false);

        assertThatThrownBy(() -> joinRequestService.cancelJoinRequest(USER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void cancelJoinRequest_requestNotFound_throws_CHAT_APPLICATION_NOT_FOUND() {
        // 존재하지 않는 신청 취소 — CHAT_APPLICATION_NOT_FOUND
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        given(joinRequestRepository.findById(REQUEST_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> joinRequestService.cancelJoinRequest(USER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_APPLICATION_NOT_FOUND);
    }

    @Test
    void cancelJoinRequest_chatRoomIdMismatch_throws_CHAT_APPLICATION_NOT_FOUND() {
        // 경로 chatRoomId와 신청 chatRoomId 불일치 — CHAT_APPLICATION_NOT_FOUND
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        JoinRequest req = mock(JoinRequest.class);
        given(req.getChatRoomId()).willReturn(999L);
        given(joinRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(req));

        assertThatThrownBy(() -> joinRequestService.cancelJoinRequest(USER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_APPLICATION_NOT_FOUND);
    }

    @Test
    void cancelJoinRequest_notApplicant_throws_ACCESS_DENIED() {
        // 신청자 본인이 아닌 사용자 취소 시도 — ACCESS_DENIED
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        JoinRequest req = mock(JoinRequest.class);
        given(req.getChatRoomId()).willReturn(ROOM_ID);
        given(req.getUserId()).willReturn(999L);
        given(joinRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(req));

        assertThatThrownBy(() -> joinRequestService.cancelJoinRequest(USER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void cancelJoinRequest_alreadyProcessed_throws_CHAT_APPLICATION_ALREADY_PROCESSED() {
        // 이미 처리된 신청 취소 — deleteIfPending 영향 행 0 → CHAT_APPLICATION_ALREADY_PROCESSED
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        JoinRequest req = mock(JoinRequest.class);
        given(req.getChatRoomId()).willReturn(ROOM_ID);
        given(req.getUserId()).willReturn(USER_ID);
        given(joinRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(req));
        given(joinRequestRepository.deleteIfPending(REQUEST_ID)).willReturn(0);

        assertThatThrownBy(() -> joinRequestService.cancelJoinRequest(USER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_APPLICATION_ALREADY_PROCESSED);
    }

    // ─── rejectJoinRequest ────────────────────────────────────────────────────

    @Test
    void rejectJoinRequest_success() {
        // 정상 거절 — SELECT FOR UPDATE 후 조건부 UPDATE, reject() 호출 검증
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        ChatRoomMember owner = stubOwnerMember();
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(owner));

        JoinRequest joinRequest = mock(JoinRequest.class);
        given(joinRequest.getChatRoomId()).willReturn(ROOM_ID);
        given(joinRequest.getId()).willReturn(REQUEST_ID);
        // reject()는 mock 기본 no-op — getStatus()는 REJECTED 고정 반환으로 응답 검증
        given(joinRequest.getStatus()).willReturn(JoinRequestStatus.REJECTED);
        // SELECT FOR UPDATE — approve↔reject 경합 직렬화용 비관적 락 조회
        given(joinRequestRepository.findByIdWithLock(REQUEST_ID)).willReturn(Optional.of(joinRequest));
        // 조건부 UPDATE 성공 — 영향 행 1 반환
        given(joinRequestRepository.rejectIfPending(REQUEST_ID)).willReturn(1);

        RejectJoinRequestResponse response =
                joinRequestService.rejectJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID);

        verify(joinRequest).reject();
        assertThat(response.joinRequestId()).isEqualTo(REQUEST_ID);
        assertThat(response.chatRoomId()).isEqualTo(ROOM_ID);
        assertThat(response.status()).isEqualTo(JoinRequestStatus.REJECTED);
    }

    @Test
    void rejectJoinRequest_roomNotFound_throws_CHAT_ROOM_NOT_FOUND() {
        // 존재하지 않는 방 거절 — CHAT_ROOM_NOT_FOUND
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(false);

        assertThatThrownBy(() -> joinRequestService.rejectJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void rejectJoinRequest_notMember_throws_CHAT_SETTING_FORBIDDEN() {
        // 채팅방 비멤버 거절 시도 — CHAT_SETTING_FORBIDDEN
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> joinRequestService.rejectJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN);
    }

    @Test
    void rejectJoinRequest_notOwner_throws_CHAT_SETTING_FORBIDDEN() {
        // 방장이 아닌 멤버 거절 시도 — CHAT_SETTING_FORBIDDEN
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        // isOwner() mock 기본값 false — OWNER_ACTIVE가 아닌 일반 멤버 시나리오
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(member));

        assertThatThrownBy(() -> joinRequestService.rejectJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN);
    }

    @Test
    void rejectJoinRequest_requestNotFound_throws_CHAT_APPLICATION_NOT_FOUND() {
        // 존재하지 않는 신청 거절 — CHAT_APPLICATION_NOT_FOUND
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        ChatRoomMember owner = stubOwnerMember();
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(owner));
        given(joinRequestRepository.findByIdWithLock(REQUEST_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> joinRequestService.rejectJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_APPLICATION_NOT_FOUND);
    }

    @Test
    void rejectJoinRequest_chatRoomIdMismatch_throws_CHAT_APPLICATION_NOT_FOUND() {
        // 경로 chatRoomId와 신청 chatRoomId 불일치 — CHAT_APPLICATION_NOT_FOUND
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        ChatRoomMember owner = stubOwnerMember();
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(owner));

        JoinRequest joinRequest = mock(JoinRequest.class);
        given(joinRequest.getChatRoomId()).willReturn(999L);
        given(joinRequestRepository.findByIdWithLock(REQUEST_ID)).willReturn(Optional.of(joinRequest));

        assertThatThrownBy(() -> joinRequestService.rejectJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_APPLICATION_NOT_FOUND);
    }

    @Test
    void rejectJoinRequest_alreadyProcessed_throws_CHAT_APPLICATION_ALREADY_PROCESSED() {
        // 이미 처리된 신청 거절 — rejectIfPending 영향 행 0 → CHAT_APPLICATION_ALREADY_PROCESSED
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);
        ChatRoomMember owner = stubOwnerMember();
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(owner));

        JoinRequest joinRequest = mock(JoinRequest.class);
        given(joinRequest.getChatRoomId()).willReturn(ROOM_ID);
        given(joinRequestRepository.findByIdWithLock(REQUEST_ID)).willReturn(Optional.of(joinRequest));
        // 조건부 UPDATE 영향 행 0 — 이미 처리된 신청 (CHAT_012)
        given(joinRequestRepository.rejectIfPending(REQUEST_ID)).willReturn(0);

        assertThatThrownBy(() -> joinRequestService.rejectJoinRequest(OWNER_ID, ROOM_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_APPLICATION_ALREADY_PROCESSED);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private JoinRequest stubPendingRequest() {
        JoinRequest req = mock(JoinRequest.class);
        given(req.getChatRoomId()).willReturn(ROOM_ID);
        // approve() 예외 또는 락 실패 시 getUserId() 미호출 — lenient 처리
        lenient().when(req.getUserId()).thenReturn(USER_ID);
        return req;
    }

    private ChatRoomMember stubOwnerMember() {
        ChatRoomMember owner = mock(ChatRoomMember.class);
        given(owner.isOwner()).willReturn(true);
        return owner;
    }

    private ErrorCode extractErrorCode(Throwable ex) {
        return ((BusinessException) ex).getErrorCode();
    }
}
