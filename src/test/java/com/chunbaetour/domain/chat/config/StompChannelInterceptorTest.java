package com.chunbaetour.domain.chat.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StompChannelInterceptorTest {

    @InjectMocks private StompChannelInterceptor interceptor;
    @Mock private TokenIssuer tokenIssuer;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private SupportRoomRepository supportRoomRepository;
    @Mock private MessageChannel channel;

    // ===== /sub/support/rooms/{id} SUBSCRIBE 인가 =====

    // 방 소유자 → 구독 허용
    @Test
    void subscribe_supportRoom_whenOwner_allowed() {
        Message<?> message = subscribeMessage("/sub/support/rooms/10", principal(1L, "ROLE_USER"));
        SupportRoom room = buildRoom(10L, 1L);
        given(supportRoomRepository.findById(10L)).willReturn(Optional.of(room));

        assertThatNoException().isThrownBy(() -> interceptor.preSend(message, channel));
    }

    // 타인 방 → CS_003 SUPPORT_ROOM_FORBIDDEN
    @Test
    void subscribe_supportRoom_whenNotOwner_throwsForbidden() {
        Message<?> message = subscribeMessage("/sub/support/rooms/10", principal(99L, "ROLE_USER"));
        SupportRoom room = buildRoom(10L, 1L);
        given(supportRoomRepository.findById(10L)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_FORBIDDEN));
    }

    // 존재하지 않는 방 → CS_001 SUPPORT_ROOM_NOT_FOUND
    @Test
    void subscribe_supportRoom_whenRoomNotFound_throwsNotFound() {
        Message<?> message = subscribeMessage("/sub/support/rooms/999", principal(1L, "ROLE_USER"));
        given(supportRoomRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_NOT_FOUND));
    }

    // ADMIN — WAITING 방(미배정) 구독 허용
    @Test
    void subscribe_supportRoom_whenAdminAndWaiting_allowed() {
        Message<?> message = subscribeMessage("/sub/support/rooms/10", principal(1L, "ROLE_ADMIN"));
        SupportRoom room = buildRoom(10L, 99L); // adminId=null
        given(supportRoomRepository.findById(10L)).willReturn(Optional.of(room));

        assertThatNoException().isThrownBy(() -> interceptor.preSend(message, channel));
    }

    // 배정된 ADMIN(본인) — IN_PROGRESS 방 구독 허용
    @Test
    void subscribe_supportRoom_whenAssignedAdmin_allowed() {
        Message<?> message = subscribeMessage("/sub/support/rooms/10", principal(1L, "ROLE_ADMIN"));
        SupportRoom room = buildRoomWithAdmin(10L, 99L, 1L); // adminId=1 (본인)
        given(supportRoomRepository.findById(10L)).willReturn(Optional.of(room));

        assertThatNoException().isThrownBy(() -> interceptor.preSend(message, channel));
    }

    // 다른 ADMIN(미담당) — IN_PROGRESS 방 구독 차단
    @Test
    void subscribe_supportRoom_whenOtherAdmin_throwsForbidden() {
        Message<?> message = subscribeMessage("/sub/support/rooms/10", principal(2L, "ROLE_ADMIN")); // adminId=1인 방
        SupportRoom room = buildRoomWithAdmin(10L, 99L, 1L); // adminId=1
        given(supportRoomRepository.findById(10L)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_FORBIDDEN));
    }

    // ===== SEND 브로커 destination 직접 발사 차단 =====

    // SEND → /sub/* 직접 → INVALID_REQUEST
    @Test
    void send_toBrokerSubDestination_throwsInvalidRequest() {
        Message<?> message = sendMessage("/sub/support/rooms/10", principal(1L, "ROLE_USER"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    // SEND → /queue/* 직접 → INVALID_REQUEST
    @Test
    void send_toBrokerQueueDestination_throwsInvalidRequest() {
        Message<?> message = sendMessage("/queue/errors", principal(1L, "ROLE_USER"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    // SEND → /pub/* (정상 경로) → 허용
    @Test
    void send_toAppDestination_allowed() {
        Message<?> message = sendMessage("/pub/support/rooms/10/messages", principal(1L, "ROLE_USER"));

        assertThatNoException().isThrownBy(() -> interceptor.preSend(message, channel));
    }

    private Message<?> subscribeMessage(String destination, UsernamePasswordAuthenticationToken principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        accessor.setSessionId("test-session");
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> sendMessage(String destination, UsernamePasswordAuthenticationToken principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        accessor.setSessionId("test-session");
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private UsernamePasswordAuthenticationToken principal(Long userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private SupportRoom buildRoom(Long id, Long userId) {
        SupportRoom room = SupportRoom.builder().userId(userId).build();
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    private SupportRoom buildRoomWithAdmin(Long id, Long userId, Long adminId) {
        SupportRoom room = SupportRoom.builder().userId(userId).build();
        ReflectionTestUtils.setField(room, "id", id);
        ReflectionTestUtils.setField(room, "adminId", adminId);
        ReflectionTestUtils.setField(room, "status", com.chunbaetour.domain.cs.entity.SupportRoomStatus.IN_PROGRESS);
        return room;
    }
}
