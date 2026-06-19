package com.chunbaetour.domain.cs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.cs.dto.request.SupportRoomCloseRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.cs.dto.request.SupportRoomCreateRequest;
import com.chunbaetour.domain.cs.dto.response.SupportMessageResponse;
import com.chunbaetour.domain.cs.dto.response.SupportRoomResponse;
import com.chunbaetour.domain.cs.entity.SupportMessage;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import com.chunbaetour.domain.cs.event.SupportRoomClosedEvent;
import com.chunbaetour.domain.cs.repository.SupportMessageRepository;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import com.chunbaetour.domain.cs.storage.SupportFileStorage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SupportRoomServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);

    @InjectMocks private SupportRoomService supportRoomService;
    @Mock private Clock clock;
    @Mock private SupportRoomRepository supportRoomRepository;
    @Mock private SupportMessageRepository supportMessageRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private SupportFileStorage supportFileStorage;

    // ===== createRoom =====

    // WAITING 상담방 이미 있으면 CS_004 예외
    @Test
    void createRoom_whenWaitingRoomExists_throwsAlreadyExists() {
        given(supportRoomRepository.existsByUserIdAndStatusIn(eq(1L), any()))
                .willReturn(true);

        assertThatThrownBy(() -> supportRoomService.createRoom(1L, new SupportRoomCreateRequest(null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_ALREADY_EXISTS));
        verify(supportRoomRepository, never()).save(any(SupportRoom.class));
    }

    // initialMessage 없으면 SupportMessage 저장 안 함
    @Test
    void createRoom_withoutMessage_doesNotSaveMessage() {
        SupportRoom room = buildRoom(1L);
        given(supportRoomRepository.existsByUserIdAndStatusIn(eq(1L), any())).willReturn(false);
        given(supportRoomRepository.save(any(SupportRoom.class))).willReturn(room);

        supportRoomService.createRoom(1L, new SupportRoomCreateRequest(null));

        verify(supportMessageRepository, never()).save(any(SupportMessage.class));
    }

    // initialMessage blank이면 SupportMessage 저장 안 함
    @Test
    void createRoom_withBlankMessage_doesNotSaveMessage() {
        SupportRoom room = buildRoom(1L);
        given(supportRoomRepository.existsByUserIdAndStatusIn(eq(1L), any())).willReturn(false);
        given(supportRoomRepository.save(any(SupportRoom.class))).willReturn(room);

        supportRoomService.createRoom(1L, new SupportRoomCreateRequest("   "));

        verify(supportMessageRepository, never()).save(any(SupportMessage.class));
    }

    // initialMessage 제공 시 SupportMessage 저장 — senderId/senderRole/content/messageType 검증
    @Test
    void createRoom_withMessage_savesMessageWithCorrectFields() {
        SupportRoom room = buildRoom(1L);
        given(supportRoomRepository.existsByUserIdAndStatusIn(eq(1L), any())).willReturn(false);
        given(supportRoomRepository.save(any(SupportRoom.class))).willReturn(room);

        supportRoomService.createRoom(1L, new SupportRoomCreateRequest("결제가 안 됩니다."));

        verify(supportMessageRepository).save(argThat(msg ->
                msg.getSupportRoomId().equals(1L) &&
                msg.getSenderId().equals(1L) &&
                msg.getSenderRole() == SupportSenderRole.CUSTOMER &&
                msg.getMessageType() == SupportMessageType.TEXT &&
                "결제가 안 됩니다.".equals(msg.getContent())
        ));
    }

    // 생성된 상담방 status = WAITING
    @Test
    void createRoom_statusIsWaiting() {
        SupportRoom room = buildRoom(1L);
        given(supportRoomRepository.existsByUserIdAndStatusIn(eq(1L), any())).willReturn(false);
        given(supportRoomRepository.save(any(SupportRoom.class))).willReturn(room);

        SupportRoomResponse result = supportRoomService.createRoom(1L, new SupportRoomCreateRequest(null));

        assertThat(result.status()).isEqualTo(SupportRoomStatus.WAITING);
    }

    // ===== closeRoom =====

    // 정상 종료 — closeIfOpen 1 row → 이벤트 단일 발행, CLOSED 응답 반환
    @Test
    void closeRoom_success_returnsClosedRoom_andPublishesEvent() {
        SupportRoom closed = buildRoomWithAdminAndStatus(1L, 1L, null, SupportRoomStatus.CLOSED);
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
        given(clock.getZone()).willReturn(FIXED_CLOCK.getZone());
        given(supportRoomRepository.closeIfOpen(eq(1L), eq(SupportRoomStatus.CLOSED), any(), eq("해결 완료")))
                .willReturn(1);
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(closed));

        SupportRoomResponse result = supportRoomService.closeRoom(1L, new SupportRoomCloseRequest("해결 완료"));

        assertThat(result.status()).isEqualTo(SupportRoomStatus.CLOSED);
        verify(applicationEventPublisher).publishEvent(new SupportRoomClosedEvent(1L, 1L));
    }

    // 이미 CLOSED 방 — closeIfOpen 0 rows → CS_002, 이벤트 미발행 (동시 종료 경합 방어 검증)
    @Test
    void closeRoom_whenAlreadyClosed_throwsAlreadyClosed_andNoEvent() {
        SupportRoom closed = buildRoomWithAdminAndStatus(1L, 1L, null, SupportRoomStatus.CLOSED);
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
        given(clock.getZone()).willReturn(FIXED_CLOCK.getZone());
        given(supportRoomRepository.closeIfOpen(eq(1L), eq(SupportRoomStatus.CLOSED), any(), any()))
                .willReturn(0);
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(closed));

        assertThatThrownBy(() -> supportRoomService.closeRoom(1L, new SupportRoomCloseRequest(null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_ALREADY_CLOSED));
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    // 존재하지 않는 방 → CS_001 (findById가 closeIfOpen 결과보다 먼저 throw)
    @Test
    void closeRoom_whenNotFound_throwsNotFound() {
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
        given(clock.getZone()).willReturn(FIXED_CLOCK.getZone());
        given(supportRoomRepository.closeIfOpen(eq(999L), any(), any(), any())).willReturn(0);
        given(supportRoomRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> supportRoomService.closeRoom(999L, new SupportRoomCloseRequest(null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_NOT_FOUND));
    }

    // ===== getMessages =====

    // 본인 방 → 메시지 반환
    @Test
    void getMessages_whenOwner_returnsMessages() {
        SupportRoom room = buildRoom(1L);
        SupportMessage msg = buildMessage(1L, 1L);
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(supportMessageRepository.findMessagesWithCursor(eq(1L), any(), any(PageRequest.class)))
                .willReturn(List.of(msg));

        CursorPageResponse<SupportMessageResponse> result = supportRoomService.getMessages(1L, 1L, null, 20);

        assertThat(result.content()).hasSize(1);
    }

    // IMAGE 메시지 — fileUrl이 presigned GET URL로 변환되는지 확인
    @Test
    void getMessages_imageMessage_fileUrlIsPresigned() {
        SupportRoom room = buildRoom(1L);
        SupportMessage imgMsg = buildImageMessage(1L, 1L);
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(supportMessageRepository.findMessagesWithCursor(eq(1L), any(), any(PageRequest.class)))
                .willReturn(List.of(imgMsg));
        given(supportFileStorage.presignedGetUrl("support-rooms/1/uuid.jpg"))
                .willReturn("https://s3.example.com/support-rooms/1/uuid.jpg?sig=abc");

        CursorPageResponse<SupportMessageResponse> result = supportRoomService.getMessages(1L, 1L, null, 20);

        assertThat(result.content().get(0).fileUrl())
                .isEqualTo("https://s3.example.com/support-rooms/1/uuid.jpg?sig=abc");
    }

    // IMAGE presign 실패(EXTERNAL_SERVICE_ERROR) → 해당 메시지 fileUrl=null로 격하, 목록 전체 503 아님(E10 패턴)
    @Test
    void getMessages_imagePresignFails_fileUrlNullified_notPropagated() {
        SupportRoom room = buildRoom(1L);
        SupportMessage imgMsg = buildImageMessage(1L, 1L);
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(supportMessageRepository.findMessagesWithCursor(eq(1L), any(), any(PageRequest.class)))
                .willReturn(List.of(imgMsg));
        given(supportFileStorage.presignedGetUrl("support-rooms/1/uuid.jpg"))
                .willThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR));

        CursorPageResponse<SupportMessageResponse> result = supportRoomService.getMessages(1L, 1L, null, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).fileUrl()).isNull();
    }

    // 타인 방 → CS_003
    @Test
    void getMessages_whenNotOwner_throwsForbidden() {
        SupportRoom room = buildRoom(1L); // userId=1
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> supportRoomService.getMessages(99L, 1L, null, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_FORBIDDEN));
    }

    // 존재하지 않는 방 → CS_001
    @Test
    void getMessages_whenRoomNotFound_throwsNotFound() {
        given(supportRoomRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> supportRoomService.getMessages(1L, 999L, null, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_NOT_FOUND));
    }

    // ===== assignAdmin =====

    // 정상 배정 → IN_PROGRESS + adminId 설정 확인
    @Test
    void assignAdmin_success_returnsInProgressRoom_withAdminId() {
        SupportRoom assigned = buildRoomWithAdminAndStatus(1L, 1L, 99L, SupportRoomStatus.IN_PROGRESS);
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
        given(clock.getZone()).willReturn(FIXED_CLOCK.getZone());
        given(supportRoomRepository.assignIfWaiting(eq(1L), eq(99L), eq(SupportRoomStatus.IN_PROGRESS), eq(SupportRoomStatus.WAITING), any(LocalDateTime.class))).willReturn(1);
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(assigned));

        SupportRoomResponse result = supportRoomService.assignAdmin(1L, 99L);

        assertThat(result.status()).isEqualTo(SupportRoomStatus.IN_PROGRESS);
        assertThat(result.adminId()).isEqualTo(99L);
    }

    // 경합 — 0 rows affected + IN_PROGRESS 재조회 → CS_005
    @Test
    void assignAdmin_whenConcurrentConflict_throwsAlreadyAssigned() {
        SupportRoom inProgress = buildRoomWithAdminAndStatus(1L, 1L, 88L, SupportRoomStatus.IN_PROGRESS);
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
        given(clock.getZone()).willReturn(FIXED_CLOCK.getZone());
        given(supportRoomRepository.assignIfWaiting(eq(1L), eq(99L), eq(SupportRoomStatus.IN_PROGRESS), eq(SupportRoomStatus.WAITING), any(LocalDateTime.class))).willReturn(0);
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(inProgress));

        assertThatThrownBy(() -> supportRoomService.assignAdmin(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_ALREADY_ASSIGNED));
    }

    // CLOSED 방 — 0 rows affected + CLOSED 재조회 → CS_002
    @Test
    void assignAdmin_whenClosed_throwsAlreadyClosed() {
        SupportRoom closed = buildRoomWithAdminAndStatus(1L, 1L, null, SupportRoomStatus.CLOSED);
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
        given(clock.getZone()).willReturn(FIXED_CLOCK.getZone());
        given(supportRoomRepository.assignIfWaiting(eq(1L), eq(99L), eq(SupportRoomStatus.IN_PROGRESS), eq(SupportRoomStatus.WAITING), any(LocalDateTime.class))).willReturn(0);
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(closed));

        assertThatThrownBy(() -> supportRoomService.assignAdmin(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_ALREADY_CLOSED));
    }

    // ===== getMessagesAsAdmin =====

    // 존재하지 않는 방 → CS_001
    @Test
    void getMessagesAsAdmin_whenRoomNotFound_throwsNotFound() {
        given(supportRoomRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> supportRoomService.getMessagesAsAdmin(999L, null, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_NOT_FOUND));
    }

    // ===== getAllRooms =====

    // 탈퇴한 유저 포함 — nickname "(탈퇴한 사용자)" fallback
    @Test
    void getAllRooms_withDeletedUser_returnsWithdrawnNickname() {
        SupportRoom room = buildRoomWithUserId(1L, 99L);
        given(supportRoomRepository.findAllRoomsWithCursor(any(), any(), any(PageRequest.class)))
                .willReturn(List.of(room));
        given(accountRepository.findAllById(List.of(99L))).willReturn(List.of());
        given(supportMessageRepository.findLastMessagesByRoomIds(List.of(1L))).willReturn(List.of());

        CursorPageResponse<com.chunbaetour.domain.cs.dto.response.AdminSupportRoomResponse> result =
                supportRoomService.getAllRooms(null, 20, null);

        assertThat(result.content().get(0).userNickname()).isEqualTo("(탈퇴한 사용자)");
    }

    // 메시지 없는 방 — lastMessage null
    @Test
    void getAllRooms_withNoMessage_returnsNullLastMessage() {
        SupportRoom room = buildRoom(1L);
        Account account = mock(Account.class);
        given(account.getId()).willReturn(1L);
        given(account.getNickname()).willReturn("테스트유저");
        given(supportRoomRepository.findAllRoomsWithCursor(any(), any(), any(PageRequest.class)))
                .willReturn(List.of(room));
        given(accountRepository.findAllById(List.of(1L))).willReturn(List.of(account));
        given(supportMessageRepository.findLastMessagesByRoomIds(List.of(1L))).willReturn(List.of());

        CursorPageResponse<com.chunbaetour.domain.cs.dto.response.AdminSupportRoomResponse> result =
                supportRoomService.getAllRooms(null, 20, null);

        assertThat(result.content().get(0).lastMessage()).isNull();
        // KAN-325: 부분 페이지(1건)여도 size는 요청값(20) echo — 조립형 ofAssembled가 content.size() 아닌 요청 size를 싣는지 고정
        assertThat(result.size()).isEqualTo(20);
    }

    // KAN-325 회귀: 빈 결과여도 size 필드는 요청값(20)을 echo — 이전 빈 가드는 size=0을 하드코딩했음(split-brain).
    // 조립형 ofAssembled로 단일화하며 빈 페이지도 요청 size를 echo하도록 바뀐 계약을 고정.
    @Test
    void getAllRooms_empty_sizeEchoesRequestNotZero() {
        given(supportRoomRepository.findAllRoomsWithCursor(any(), any(), any(PageRequest.class)))
                .willReturn(List.of());

        CursorPageResponse<com.chunbaetour.domain.cs.dto.response.AdminSupportRoomResponse> result =
                supportRoomService.getAllRooms(null, 20, null);

        assertThat(result.content()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.size()).isEqualTo(20);
    }

    // ===== getMyRooms =====

    // hasNext=true when result > size
    @Test
    void getMyRooms_hasNextTrue_whenResultExceedsSize() {
        given(supportRoomRepository.findMyRoomsWithCursor(eq(1L), any(), any(), any(PageRequest.class)))
                .willReturn(List.of(buildRoom(1L), buildRoom(2L), buildRoom(3L)));

        CursorPageResponse<SupportRoomResponse> result = supportRoomService.getMyRooms(1L, null, 2, null);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.content()).hasSize(2);
    }

    // KAN-325 회귀: 부분 페이지(1건, size=5 요청) → size 필드는 실제 개수(1)가 아니라 요청 size(5)를 echo (계산형 of() 수렴)
    @Test
    void getMyRooms_partialPage_sizeEchoesRequest() {
        given(supportRoomRepository.findMyRoomsWithCursor(eq(1L), any(), any(), any(PageRequest.class)))
                .willReturn(List.of(buildRoom(1L)));

        CursorPageResponse<SupportRoomResponse> result = supportRoomService.getMyRooms(1L, null, 5, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.size()).isEqualTo(5);
    }

    private SupportRoom buildRoomWithUserId(Long id, Long userId) {
        SupportRoom room = SupportRoom.builder().userId(userId).build();
        try {
            var field = SupportRoom.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(room, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return room;
    }

    private SupportRoom buildRoom(Long id) {
        SupportRoom room = SupportRoom.builder().userId(1L).build();
        try {
            var field = SupportRoom.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(room, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return room;
    }

    private SupportRoom buildRoomWithAdminAndStatus(Long id, Long userId, Long adminId, SupportRoomStatus status) {
        SupportRoom room = SupportRoom.builder().userId(userId).build();
        try {
            var idField = SupportRoom.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(room, id);
            var statusField = SupportRoom.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(room, status);
            if (adminId != null) {
                var adminIdField = SupportRoom.class.getDeclaredField("adminId");
                adminIdField.setAccessible(true);
                adminIdField.set(room, adminId);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return room;
    }

    private SupportMessage buildImageMessage(Long id, Long roomId) {
        SupportMessage msg = SupportMessage.builder()
                .supportRoomId(roomId)
                .senderId(1L)
                .senderRole(SupportSenderRole.CUSTOMER)
                .messageType(SupportMessageType.IMAGE)
                .fileUrl("support-rooms/1/uuid.jpg")
                .fileName("photo.jpg")
                .fileSize(1024L)
                .build();
        try {
            var field = SupportMessage.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(msg, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return msg;
    }

    private SupportMessage buildMessage(Long id, Long roomId) {
        SupportMessage msg = SupportMessage.builder()
                .supportRoomId(roomId)
                .senderId(1L)
                .senderRole(SupportSenderRole.CUSTOMER)
                .messageType(SupportMessageType.TEXT)
                .content("테스트 메시지")
                .fileUrl(null)
                .build();
        try {
            var field = SupportMessage.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(msg, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return msg;
    }
}
