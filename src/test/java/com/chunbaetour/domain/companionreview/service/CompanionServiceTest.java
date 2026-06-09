package com.chunbaetour.domain.companionreview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.companionreview.dto.request.CompanionAddParticipantsRequest;
import com.chunbaetour.domain.companionreview.dto.request.CompanionStartRequest;
import com.chunbaetour.domain.companionreview.dto.response.CompanionAddParticipantsResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionEndResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionStartResponse;
import com.chunbaetour.domain.companionreview.entity.Companion;
import com.chunbaetour.domain.companionreview.repository.CompanionParticipantRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CompanionServiceTest {

    @InjectMocks private CompanionService companionService;
    @Mock private CompanionRepository companionRepository;
    @Mock private CompanionParticipantRepository companionParticipantRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;

    // ===== startCompanion =====

    // 정상 시작 — 방장 + 참여자 저장, 응답 반환
    @Test
    void startCompanion_success_returnsResponse() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        CompanionStartRequest request = new CompanionStartRequest(List.of(2L));
        Companion companion = Companion.builder().chatRoomId(roomId).build();
        // JPA save 후 ID 세팅 시뮬레이션 — CompanionParticipant 빌더 검증 통과용
        ReflectionTestUtils.setField(companion, "id", 100L);

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(roomId)).willReturn(Optional.empty());
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(any(), any(), any()))
                .willReturn(true);
        given(companionRepository.save(any())).willReturn(companion);
        given(companionParticipantRepository.saveAll(any())).willReturn(List.of());

        CompanionStartResponse response = companionService.startCompanion(ownerId, roomId, request);

        assertThat(response.status()).isEqualTo("ONGOING");
        assertThat(response.participantUserIds()).containsExactlyInAnyOrder(2L, 1L);
    }

    // 방 없음 → CHAT_001
    @Test
    void startCompanion_roomNotFound_throwsRoomNotFound() {
        given(chatRoomRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> companionService.startCompanion(1L, 10L, new CompanionStartRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND));
        verify(companionRepository, never()).save(any());
    }

    // 방장 아님 → CHAT_006
    @Test
    void startCompanion_notOwner_throwsForbidden() {
        Long ownerId = 1L;
        Long otherUser = 2L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> companionService.startCompanion(otherUser, 10L, new CompanionStartRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN));
        verify(companionRepository, never()).save(any());
    }

    // CLOSED 방 → CHAT_013
    @Test
    void startCompanion_closedRoom_throwsRoomClosed() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        chatRoom.close();

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));

        assertThatThrownBy(() -> companionService.startCompanion(ownerId, 10L, new CompanionStartRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_ROOM_CLOSED));
        verify(companionRepository, never()).save(any());
    }

    // 같은 방에 ENDED 동행 존재 → CR_004
    @Test
    void startCompanion_endedCompanionExists_throwsAlreadyExists() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion endedCompanion = Companion.builder().chatRoomId(10L).build();
        endedCompanion.end();

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(endedCompanion));

        assertThatThrownBy(() -> companionService.startCompanion(ownerId, 10L, new CompanionStartRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_ALREADY_EXISTS));
        verify(companionRepository, never()).save(any());
    }

    // 같은 방에 ONGOING 동행 존재 → CR_007
    @Test
    void startCompanion_ongoingCompanionExists_throwsAlreadyStarted() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion ongoingCompanion = Companion.builder().chatRoomId(10L).build();

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(ongoingCompanion));

        assertThatThrownBy(() -> companionService.startCompanion(ownerId, 10L, new CompanionStartRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_ALREADY_STARTED));
        verify(companionRepository, never()).save(any());
    }

    // 참여자가 ACTIVE 멤버 아님 → CHAT_005
    @Test
    void startCompanion_participantNotMember_throwsNotJoined() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.empty());
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(any(), any(), any()))
                .willReturn(false);

        assertThatThrownBy(() -> companionService.startCompanion(ownerId, 10L, new CompanionStartRequest(List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_NOT_JOINED));
        verify(companionRepository, never()).save(any());
    }

    // ===== addParticipants =====

    // 정상 추가 — DB 저장, 추가된 userId 목록 반환
    @Test
    void addParticipants_success_returnsAddedUserIds() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion companion = Companion.builder().chatRoomId(roomId).build();
        ReflectionTestUtils.setField(companion, "id", 100L);
        CompanionAddParticipantsRequest request = new CompanionAddParticipantsRequest(List.of(2L, 3L));

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(roomId)).willReturn(Optional.of(companion));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(any(), any(), any()))
                .willReturn(true);
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

    // 동행 없음 → CR_005
    @Test
    void addParticipants_companionNotFound_throwsNotFound() {
        Long ownerId = 1L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.empty());

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
        Companion endedCompanion = Companion.builder().chatRoomId(10L).build();
        endedCompanion.end();

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(endedCompanion));

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
        Companion companion = Companion.builder().chatRoomId(10L).build();
        ReflectionTestUtils.setField(companion, "id", 100L);

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(companion));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(any(), any(), any()))
                .willReturn(false);

        assertThatThrownBy(() -> companionService.addParticipants(ownerId, 10L,
                new CompanionAddParticipantsRequest(List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_NOT_JOINED));
        verify(companionParticipantRepository, never()).saveAll(any());
    }

    // 이미 참여자 → CR_008
    @Test
    void addParticipants_duplicateParticipant_throwsAlreadyExists() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion companion = Companion.builder().chatRoomId(roomId).build();
        ReflectionTestUtils.setField(companion, "id", 100L);

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(roomId)).willReturn(Optional.of(companion));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndMemberStateIn(any(), any(), any()))
                .willReturn(true);
        given(companionParticipantRepository.saveAll(any()))
                .willThrow(new DataIntegrityViolationException("uq_companion_participant"));

        assertThatThrownBy(() -> companionService.addParticipants(ownerId, roomId,
                new CompanionAddParticipantsRequest(List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_PARTICIPANT_ALREADY_EXISTS));
    }

    // ===== endCompanion =====

    // 정상 종료 — status ENDED, endedAt 설정
    @Test
    void endCompanion_success_returnsEndedResponse() {
        Long ownerId = 1L;
        Long roomId = 10L;
        ChatRoom chatRoom = ChatRoom.createWithOwner(100L, ownerId, "테스트방", null, 5);
        Companion companion = Companion.builder().chatRoomId(roomId).build();

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(roomId)).willReturn(Optional.of(companion));

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
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.empty());

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
        Companion endedCompanion = Companion.builder().chatRoomId(10L).build();
        endedCompanion.end();

        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
        given(companionRepository.findByChatRoomId(10L)).willReturn(Optional.of(endedCompanion));

        assertThatThrownBy(() -> companionService.endCompanion(ownerId, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMPANION_ALREADY_ENDED));
    }
}
