package com.chunbaetour.domain.cs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.ratelimit.RateLimitDecision;
import com.chunbaetour.domain.common.ratelimit.RateLimiter;
import com.chunbaetour.domain.cs.dto.request.SupportSendMessageRequest;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.repository.SupportMessageRepository;
import com.chunbaetour.domain.cs.entity.SupportMessage;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupportMessageServiceTest {

    @InjectMocks private SupportMessageService supportMessageService;
    @Mock private SupportRoomRepository supportRoomRepository;
    @Mock private SupportMessageRepository supportMessageRepository;
    @Mock private SupportRedisPubSubService supportRedisPubSubService;
    @Mock private RateLimiter rateLimiter;

    // rate limit 초과 → TOO_MANY_REQUESTS, 이후 DB 조회 없음
    @Test
    void sendMessage_whenRateLimitExceeded_throwsTooManyRequests() {
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.denied(Duration.ofSeconds(5)));

        assertThatThrownBy(() -> supportMessageService.sendMessage(1L, 10L, false, req("안녕하세요")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
        verify(supportRoomRepository, never()).findById(any());
    }

    // 존재하지 않는 방 → CS_001
    @Test
    void sendMessage_whenRoomNotFound_throwsNotFound() {
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(19));
        given(supportRoomRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> supportMessageService.sendMessage(1L, 999L, false, req("안녕")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_NOT_FOUND));
    }

    // CLOSED 방 → CS_002
    @Test
    void sendMessage_whenRoomClosed_throwsAlreadyClosed() {
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(19));
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(buildRoom(1L, 1L, SupportRoomStatus.CLOSED)));

        assertThatThrownBy(() -> supportMessageService.sendMessage(1L, 1L, false, req("안녕")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_ALREADY_CLOSED));
    }

    // ADMIN이 WAITING 방에 발신 → CS_003
    @Test
    void sendMessage_whenAdminSendsToWaitingRoom_throwsForbidden() {
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(19));
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(buildRoom(1L, 99L, SupportRoomStatus.WAITING)));

        assertThatThrownBy(() -> supportMessageService.sendMessage(1L, 1L, true, req("안녕")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_FORBIDDEN));
    }

    // USER가 타인 방에 발신 → CS_003
    @Test
    void sendMessage_whenUserSendsToOtherRoom_throwsForbidden() {
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(19));
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(buildRoom(1L, 99L, SupportRoomStatus.WAITING)));

        assertThatThrownBy(() -> supportMessageService.sendMessage(1L, 1L, false, req("안녕")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_FORBIDDEN));
    }

    // 배정되지 않은 ADMIN이 IN_PROGRESS 방 발신 시도 → CS_003
    @Test
    void sendMessage_whenAdminNotAssigned_throwsForbidden() {
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(19));
        SupportRoom room = buildRoom(1L, 99L, SupportRoomStatus.IN_PROGRESS); // adminId=null (buildRoom doesn't set adminId)
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> supportMessageService.sendMessage(1L, 1L, true, req("안녕")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_FORBIDDEN));
    }

    // null content → INVALID_REQUEST
    @Test
    void sendMessage_whenContentNull_throwsInvalidRequest() {
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(19));
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(buildRoom(1L, 1L, SupportRoomStatus.WAITING)));

        assertThatThrownBy(() -> supportMessageService.sendMessage(1L, 1L, false, req(null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(supportMessageRepository, never()).save(any());
    }

    // blank content → INVALID_REQUEST
    @Test
    void sendMessage_whenContentBlank_throwsInvalidRequest() {
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(19));
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(buildRoom(1L, 1L, SupportRoomStatus.WAITING)));

        assertThatThrownBy(() -> supportMessageService.sendMessage(1L, 1L, false, req("   ")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(supportMessageRepository, never()).save(any());
    }

    // 1001자 메시지 → MESSAGE_TOO_LONG, DB 저장 없음
    @Test
    void sendMessage_whenContentTooLong_throwsMessageTooLong() {
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(19));
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(buildRoom(1L, 1L, SupportRoomStatus.WAITING)));

        assertThatThrownBy(() -> supportMessageService.sendMessage(1L, 1L, false, req("a".repeat(1001))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_TOO_LONG));
        verify(supportMessageRepository, never()).save(any());
    }

    // ===== 성공 경로 =====

    // USER 소유자 발신 — save·publish 호출 확인 (트랜잭션 없는 환경 → else 분기 즉시 publish)
    @Test
    void sendMessage_whenOwnerSends_savesAndPublishes() {
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(19));
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(buildRoom(1L, 1L, SupportRoomStatus.WAITING)));
        given(supportMessageRepository.save(any())).willReturn(buildMessage(100L, 1L));

        supportMessageService.sendMessage(1L, 1L, false, req("안녕하세요"));

        verify(supportMessageRepository).save(any(SupportMessage.class));
        verify(supportRedisPubSubService).publish(eq(1L), any());
    }

    // ADMIN IN_PROGRESS 방 발신 — save·publish 호출 확인
    @Test
    void sendMessage_whenAdminSendsToInProgressRoom_savesAndPublishes() {
        given(rateLimiter.tryConsume(any(), any())).willReturn(RateLimitDecision.allowed(19));
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(buildRoom(1L, 99L, SupportRoomStatus.IN_PROGRESS)));
        given(supportMessageRepository.save(any())).willReturn(buildMessage(101L, 1L));

        supportMessageService.sendMessage(1L, 1L, true, req("확인했습니다"));

        verify(supportMessageRepository).save(any(SupportMessage.class));
        verify(supportRedisPubSubService).publish(eq(1L), any());
    }

    private SupportSendMessageRequest req(String content) {
        return new SupportSendMessageRequest(content);
    }

    private SupportMessage buildMessage(Long id, Long roomId) {
        SupportMessage msg = SupportMessage.builder()
                .supportRoomId(roomId)
                .senderId(1L)
                .senderRole(SupportSenderRole.CUSTOMER)
                .messageType(SupportMessageType.TEXT)
                .content("테스트")
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

    private SupportRoom buildRoom(Long id, Long userId, SupportRoomStatus status) {
        SupportRoom room = SupportRoom.builder().userId(userId).build();
        try {
            var idField = SupportRoom.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(room, id);
            var statusField = SupportRoom.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(room, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return room;
    }
}
