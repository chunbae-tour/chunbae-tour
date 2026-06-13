package com.chunbaetour.domain.companionreview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.companionreview.dto.request.CompanionAddParticipantsRequest;
import com.chunbaetour.domain.companionreview.dto.request.CompanionCreateRequest;
import com.chunbaetour.domain.companionreview.dto.response.CompanionAddParticipantsResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionCreateResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionEndResponse;
import com.chunbaetour.domain.companionreview.entity.Companion;
import com.chunbaetour.domain.companionreview.repository.CompanionParticipantRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionTripPeriodProjection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class CompanionServiceTest {

    @InjectMocks private CompanionService companionService;
    @Mock private CompanionRepository companionRepository;
    @Mock private CompanionParticipantRepository companionParticipantRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private RedissonClient redissonClient;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private RLock lock;

    private static final LocalDate TRIP_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate TRIP_END = LocalDate.of(2026, 7, 5);

    // 분산 락/트랜잭션 mock — 락 성공·즉시 커밋으로 본 로직만 검증 (락 자체 실패/타임아웃은 별도 테스트에서 override)
    @BeforeEach
    void setUp() throws InterruptedException {
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(redissonClient.getMultiLock(any(RLock[].class))).thenReturn(lock);
        lenient().when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);

        TransactionStatus txStatus = mock(TransactionStatus.class);
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(txStatus);
    }

    // ===== createCompanion =====

    // 정상 생성 — 방장 + 참여자 저장, 응답 반환
    @Test
    void createCompanion_success_returnsResponse() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        CompanionCreateRequest request = new CompanionCreateRequest(List.of(2L), TRIP_START, TRIP_END);
        Companion companion = Companion.builder().chatRoomId(roomId).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();
        // JPA save 후 ID 세팅 시뮬레이션 — CompanionParticipant 빌더 검증 통과용
        ReflectionTestUtils.setField(companion, "id", 100L);

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(roomId)).willReturn(Optional.empty());
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(any(), any(), any()))
                .willReturn(2L);
        given(companionRepository.save(any())).willReturn(companion);
        given(companionParticipantRepository.saveAll(any())).willReturn(List.of());

        CompanionCreateResponse response = companionService.createCompanion(ownerId, roomId, request);

        assertThat(response.status()).isEqualTo("ONGOING");
        assertThat(response.participantUserIds()).containsExactlyInAnyOrder(2L, 1L);
    }

    // 방 없음 → CHAT_001
    @Test
    void createCompanion_roomNotFound_throwsRoomNotFound() {
        given(chatRoomRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> companionService.createCompanion(1L, 10L,
                new CompanionCreateRequest(List.of(), TRIP_START, TRIP_END)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND));
        verify(companionRepository, never()).save(any());
    }

    // 방장 아님 → CHAT_006
    @Test
    void createCompanion_notOwner_throwsForbidden() {
        Long ownerId = 1L;
        Long otherUser = 2L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> companionService.createCompanion(otherUser, 10L,
                new CompanionCreateRequest(List.of(), TRIP_START, TRIP_END)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN));
        verify(companionRepository, never()).save(any());
    }

    // CLOSED 방 → CHAT_013
    @Test
    void createCompanion_closedRoom_throwsRoomClosed() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        chatRoom.close();

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> companionService.createCompanion(ownerId, 10L,
                new CompanionCreateRequest(List.of(), TRIP_START, TRIP_END)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_ROOM_CLOSED));
        verify(companionRepository, never()).save(any());
    }

    // 같은 방에 ENDED 동행 존재 → CR_004
    @Test
    void createCompanion_endedCompanionExists_throwsAlreadyExists() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion endedCompanion = Companion.builder().chatRoomId(10L).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();
        endedCompanion.end();

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(endedCompanion));

        assertThatThrownBy(() -> companionService.createCompanion(ownerId, 10L,
                new CompanionCreateRequest(List.of(), TRIP_START, TRIP_END)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_ALREADY_EXISTS));
        verify(companionRepository, never()).save(any());
    }

    // 같은 방에 ONGOING 동행 존재 → CR_007
    @Test
    void createCompanion_ongoingCompanionExists_throwsAlreadyStarted() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion ongoingCompanion = Companion.builder().chatRoomId(10L).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(ongoingCompanion));

        assertThatThrownBy(() -> companionService.createCompanion(ownerId, 10L,
                new CompanionCreateRequest(List.of(), TRIP_START, TRIP_END)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_ALREADY_STARTED));
        verify(companionRepository, never()).save(any());
    }

    // 방장이 ACTIVE 멤버 아님(채팅방 나간 상태) → CHAT_005
    @Test
    void createCompanion_ownerNotActiveMember_throwsNotJoined() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        List<ChatMemberState> activeStates = List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.empty());
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(10L, List.of(2L, ownerId), activeStates))
                .willReturn(1L);

        assertThatThrownBy(() -> companionService.createCompanion(ownerId, 10L,
                new CompanionCreateRequest(List.of(2L), TRIP_START, TRIP_END)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_NOT_JOINED));
        verify(companionRepository, never()).save(any());
    }

    // 참여자가 ACTIVE 멤버 아님 → CHAT_005
    @Test
    void createCompanion_participantNotMember_throwsNotJoined() {
        Long ownerId = 1L;
        Long participantId = 2L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        List<ChatMemberState> activeStates = List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.empty());
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(10L, List.of(participantId, ownerId), activeStates))
                .willReturn(1L);

        assertThatThrownBy(() -> companionService.createCompanion(ownerId, 10L,
                new CompanionCreateRequest(List.of(participantId), TRIP_START, TRIP_END)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_NOT_JOINED));
        verify(companionRepository, never()).save(any());
    }

    // tripEndDate < tripStartDate → COMMON_004(INVALID_INPUT_VALUE)
    @Test
    void createCompanion_tripEndBeforeStart_throwsInvalidInputValue() {
        assertThatThrownBy(() -> companionService.createCompanion(1L, 10L,
                new CompanionCreateRequest(List.of(), TRIP_END, TRIP_START)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(companionRepository, never()).save(any());
    }

    // 방장 또는 참여자가 다른 ONGOING 동행과 기간 겹침 → CR_010
    @Test
    void createCompanion_dateOverlap_throwsDateOverlap() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(roomId)).willReturn(Optional.empty());
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(any(), any(), any()))
                .willReturn(2L);
        // 참여자(2L)가 이미 같은 기간에 다른 ONGOING 동행 참여 중
        given(companionParticipantRepository.findOngoingTripPeriodsByUserIds(any(), any()))
                .willReturn(List.of(new TripPeriod(2L, TRIP_START, TRIP_END)));

        assertThatThrownBy(() -> companionService.createCompanion(ownerId, roomId,
                new CompanionCreateRequest(List.of(2L), TRIP_START, TRIP_END)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_DATE_OVERLAP));
        verify(companionRepository, never()).save(any());
    }

    // 경계값 — 신규 기간 시작일이 기존 동행 종료일과 같은 날(맞닿음)이면 겹침으로 판단 → CR_010
    @Test
    void createCompanion_dateOverlapBoundaryTouching_throwsDateOverlap() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        LocalDate otherStart = TRIP_START.minusDays(10);
        LocalDate otherEnd = TRIP_START; // 신규 동행 시작일과 동일

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(roomId)).willReturn(Optional.empty());
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(any(), any(), any()))
                .willReturn(1L);
        given(companionParticipantRepository.findOngoingTripPeriodsByUserIds(any(), any()))
                .willReturn(List.of(new TripPeriod(ownerId, otherStart, otherEnd)));

        assertThatThrownBy(() -> companionService.createCompanion(ownerId, roomId,
                new CompanionCreateRequest(List.of(), TRIP_START, TRIP_END)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_DATE_OVERLAP));
    }

    // 기간이 겹치지 않으면 정상 생성됨
    @Test
    void createCompanion_nonOverlappingPeriod_succeeds() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion companion = Companion.builder().chatRoomId(roomId).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();
        ReflectionTestUtils.setField(companion, "id", 100L);
        // 기존 ONGOING 동행 기간은 신규 기간(TRIP_START~TRIP_END) 이전에 종료됨 — 겹치지 않음
        LocalDate otherStart = TRIP_START.minusDays(10);
        LocalDate otherEnd = TRIP_START.minusDays(2);

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(roomId)).willReturn(Optional.empty());
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(any(), any(), any()))
                .willReturn(1L);
        given(companionParticipantRepository.findOngoingTripPeriodsByUserIds(any(), any()))
                .willReturn(List.of(new TripPeriod(ownerId, otherStart, otherEnd)));
        given(companionRepository.save(any())).willReturn(companion);
        given(companionParticipantRepository.saveAll(any())).willReturn(List.of());

        CompanionCreateResponse response = companionService.createCompanion(ownerId, roomId,
                new CompanionCreateRequest(List.of(), TRIP_START, TRIP_END));

        assertThat(response.status()).isEqualTo("ONGOING");
    }

    // uq_companions_chat_room_id TOCTOU race → CR_007
    @Test
    void createCompanion_uniqueConstraintRace_throwsAlreadyStarted() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        var constraintEx = new ConstraintViolationException(
                "dup", null, "uq_companions_chat_room_id");
        var dataEx = new DataIntegrityViolationException("uq_companions_chat_room_id", constraintEx);

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(roomId)).willReturn(Optional.empty());
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(any(), any(), any()))
                .willReturn(2L);
        given(companionRepository.save(any())).willThrow(dataEx);

        assertThatThrownBy(() -> companionService.createCompanion(ownerId, roomId,
                new CompanionCreateRequest(List.of(2L), TRIP_START, TRIP_END)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_ALREADY_STARTED));
    }

    // 분산 락 획득 실패 → CONCURRENT_UPDATE, save 호출 안 됨
    @Test
    void createCompanion_lockFailed_throwsConcurrentUpdate() throws InterruptedException {
        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);
        given(lock.isHeldByCurrentThread()).willReturn(false);

        assertThatThrownBy(() -> companionService.createCompanion(1L, 10L,
                new CompanionCreateRequest(List.of(2L), TRIP_START, TRIP_END)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONCURRENT_UPDATE));
        verify(companionRepository, never()).save(any());
    }

    // ===== addParticipants =====

    // 정상 추가 — DB 저장, 추가된 userId 목록 반환
    @Test
    void addParticipants_success_returnsAddedUserIds() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion companion = Companion.builder().chatRoomId(roomId).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();
        ReflectionTestUtils.setField(companion, "id", 100L);
        CompanionAddParticipantsRequest request = new CompanionAddParticipantsRequest(List.of(2L, 3L));

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomIdWithLock(roomId)).willReturn(Optional.of(companion));
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(any(), any(), any()))
                .willReturn(2L);
        given(companionParticipantRepository.saveAll(any())).willReturn(List.of());

        CompanionAddParticipantsResponse response = companionService.addParticipants(ownerId, roomId, request);

        assertThat(response.companionId()).isEqualTo(100L);
        assertThat(response.addedUserIds()).containsExactlyInAnyOrder(2L, 3L);
    }

    // 방 없음 → CHAT_001
    @Test
    void addParticipants_roomNotFound_throwsRoomNotFound() {
        given(chatRoomRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> companionService.addParticipants(1L, 10L,
                new CompanionAddParticipantsRequest(List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND));
        verify(companionRepository, never()).save(any());
    }

    // 방장 아님 → CHAT_006
    @Test
    void addParticipants_notOwner_throwsForbidden() {
        Long ownerId = 1L;
        Long otherUser = 2L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> companionService.addParticipants(otherUser, 10L,
                new CompanionAddParticipantsRequest(List.of(3L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN));
        verify(companionParticipantRepository, never()).saveAll(any());
    }

    // CLOSED 방 → CHAT_013
    @Test
    void addParticipants_closedRoom_throwsRoomClosed() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        chatRoom.close();

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> companionService.addParticipants(ownerId, 10L,
                new CompanionAddParticipantsRequest(List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_ROOM_CLOSED));
        verify(companionParticipantRepository, never()).saveAll(any());
    }

    // 동행 없음 → CR_005
    @Test
    void addParticipants_companionNotFound_throwsNotFound() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomIdWithLock(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companionService.addParticipants(ownerId, 10L,
                new CompanionAddParticipantsRequest(List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_NOT_FOUND));
        verify(companionParticipantRepository, never()).saveAll(any());
    }

    // ENDED 동행 → CR_006
    @Test
    void addParticipants_endedCompanion_throwsAlreadyEnded() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion endedCompanion = Companion.builder().chatRoomId(10L).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();
        endedCompanion.end();

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomIdWithLock(10L)).willReturn(Optional.of(endedCompanion));

        assertThatThrownBy(() -> companionService.addParticipants(ownerId, 10L,
                new CompanionAddParticipantsRequest(List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_ALREADY_ENDED));
        verify(companionParticipantRepository, never()).saveAll(any());
    }

    // 대상이 ACTIVE 멤버 아님 → CHAT_005
    @Test
    void addParticipants_participantNotMember_throwsNotJoined() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion companion = Companion.builder().chatRoomId(10L).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();
        ReflectionTestUtils.setField(companion, "id", 100L);

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomIdWithLock(10L)).willReturn(Optional.of(companion));
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(any(), any(), any()))
                .willReturn(0L);

        assertThatThrownBy(() -> companionService.addParticipants(ownerId, 10L,
                new CompanionAddParticipantsRequest(List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_NOT_JOINED));
        verify(companionParticipantRepository, never()).saveAll(any());
    }

    // 추가 대상이 다른 ONGOING 동행과 기간 겹침 → CR_010
    @Test
    void addParticipants_dateOverlap_throwsDateOverlap() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion companion = Companion.builder().chatRoomId(roomId).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();
        ReflectionTestUtils.setField(companion, "id", 100L);

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomIdWithLock(roomId)).willReturn(Optional.of(companion));
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(any(), any(), any()))
                .willReturn(1L);
        // 추가 대상(2L)이 이미 같은 기간에 다른 ONGOING 동행 참여 중
        given(companionParticipantRepository.findOngoingTripPeriodsByUserIds(any(), any()))
                .willReturn(List.of(new TripPeriod(2L, TRIP_START, TRIP_END)));

        assertThatThrownBy(() -> companionService.addParticipants(ownerId, roomId,
                new CompanionAddParticipantsRequest(List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_DATE_OVERLAP));
        verify(companionParticipantRepository, never()).saveAll(any());
    }

    // 이미 참여자 → CR_008
    @Test
    void addParticipants_duplicateParticipant_throwsAlreadyExists() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion companion = Companion.builder().chatRoomId(roomId).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();
        ReflectionTestUtils.setField(companion, "id", 100L);

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomIdWithLock(roomId)).willReturn(Optional.of(companion));
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(any(), any(), any()))
                .willReturn(1L);
        var constraintEx = new ConstraintViolationException(
                "dup", null, "uq_companion_participant");
        given(companionParticipantRepository.saveAll(any()))
                .willThrow(new DataIntegrityViolationException("uq_companion_participant", constraintEx));

        assertThatThrownBy(() -> companionService.addParticipants(ownerId, roomId,
                new CompanionAddParticipantsRequest(List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_PARTICIPANT_ALREADY_EXISTS));
    }

    // 다른 제약 위반 → 그대로 전파
    @Test
    void addParticipants_otherConstraintViolation_propagates() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion companion = Companion.builder().chatRoomId(roomId).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();
        ReflectionTestUtils.setField(companion, "id", 100L);

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomIdWithLock(roomId)).willReturn(Optional.of(companion));
        given(chatRoomMemberRepository.countByChatRoomIdAndUserIdInAndMemberStateIn(any(), any(), any()))
                .willReturn(1L);
        var otherConstraintEx = new ConstraintViolationException(
                "fk violation", null, "fk_companion_participants_companion_id");
        given(companionParticipantRepository.saveAll(any()))
                .willThrow(new DataIntegrityViolationException("fk violation", otherConstraintEx));

        assertThatThrownBy(() -> companionService.addParticipants(ownerId, roomId,
                new CompanionAddParticipantsRequest(List.of(2L))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // 분산 락 획득 실패 → CONCURRENT_UPDATE, saveAll 호출 안 됨
    @Test
    void addParticipants_lockFailed_throwsConcurrentUpdate() throws InterruptedException {
        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);
        given(lock.isHeldByCurrentThread()).willReturn(false);

        assertThatThrownBy(() -> companionService.addParticipants(1L, 10L,
                new CompanionAddParticipantsRequest(List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONCURRENT_UPDATE));
        verify(companionParticipantRepository, never()).saveAll(any());
    }

    // ===== endCompanion =====

    // 정상 종료 — status ENDED, endedAt 설정
    @Test
    void endCompanion_success_returnsEndedResponse() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion companion = Companion.builder().chatRoomId(roomId).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomIdWithLock(roomId)).willReturn(Optional.of(companion));

        CompanionEndResponse response = companionService.endCompanion(ownerId, roomId);

        assertThat(response.status()).isEqualTo("ENDED");
        assertThat(response.endedAt()).isNotNull();
    }

    // 방장 아님 → CHAT_006
    @Test
    void endCompanion_notOwner_throwsForbidden() {
        Long ownerId = 1L;
        Long otherUser = 2L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> companionService.endCompanion(otherUser, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN));
    }

    // 동행 없음 → CR_005
    @Test
    void endCompanion_companionNotFound_throwsNotFound() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomIdWithLock(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companionService.endCompanion(ownerId, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_NOT_FOUND));
    }

    // 이미 종료된 동행 → CR_006
    @Test
    void endCompanion_alreadyEnded_throwsAlreadyEnded() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion endedCompanion = Companion.builder().chatRoomId(10L).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();
        endedCompanion.end();

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomIdWithLock(10L)).willReturn(Optional.of(endedCompanion));

        assertThatThrownBy(() -> companionService.endCompanion(ownerId, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_ALREADY_ENDED));
    }

    // findOngoingTripPeriodsByUserIds 결과 표현용 — CompanionTripPeriodProjection의 단순 테스트 구현체
    private record TripPeriod(Long userId, LocalDate tripStartDate, LocalDate tripEndDate)
            implements CompanionTripPeriodProjection {
        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public LocalDate getTripStartDate() {
            return tripStartDate;
        }

        @Override
        public LocalDate getTripEndDate() {
            return tripEndDate;
        }
    }
}
