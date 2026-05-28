package com.chunbaetour.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.notification.dto.response.NotificationResponse;
import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NotificationRedisPubSubServiceTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter counter;

    private NotificationRedisPubSubService service;

    private static final Long USER_ID = 1L;
    private static final String CHANNEL = "notification:" + USER_ID;
    private static final String STOMP_QUEUE = "/queue/notifications";

    private static final NotificationResponse RESPONSE = new NotificationResponse(
            10L,
            NotificationType.CHAT_JOIN_REQUEST,
            "참여 신청 도착",
            "채팅방 참여 신청이 도착했어요.",
            20L,
            NotificationReferenceType.JOIN_REQUEST,
            false,
            LocalDateTime.of(2026, 5, 28, 0, 0));

    @BeforeEach
    void setUp() {
        service = new NotificationRedisPubSubService(
                stringRedisTemplate, objectMapper, messagingTemplate, meterRegistry);
    }

    // publish() — notification:{userId} 채널로 직렬화된 JSON 발행 검증
    @Test
    void publish_sendsSerializedJsonToRedisChannel() throws Exception {
        String json = "{\"notificationId\":10}";
        given(objectMapper.writeValueAsString(any())).willReturn(json);

        service.publish(USER_ID, RESPONSE);

        verify(stringRedisTemplate).convertAndSend("notification:" + USER_ID, json);
    }

    // publish() — 직렬화 실패 시 예외 미전파 + 메트릭 카운터 증가
    @Test
    void publish_serializationFailure_doesNotThrow_andIncrementsMetric() throws Exception {
        given(meterRegistry.counter("notification.serialize.failure.total")).willReturn(counter);
        given(objectMapper.writeValueAsString(any())).willThrow(mock(JacksonException.class));

        assertThatNoException().isThrownBy(() -> service.publish(USER_ID, RESPONSE));
        verify(counter).increment();
    }

    // publish() — Redis 연결 실패 시 예외 미전파 + notification.publish.failure.total 카운터 증가
    @Test
    void publish_redisConnectionFailure_doesNotThrow_andIncrementsMetric() throws Exception {
        given(meterRegistry.counter("notification.publish.failure.total")).willReturn(counter);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        willThrow(new RuntimeException("Redis connection refused"))
                .given(stringRedisTemplate).convertAndSend(anyString(), anyString());

        assertThatNoException().isThrownBy(() -> service.publish(USER_ID, RESPONSE));
        verify(counter).increment();
    }

    // handleMessage() — 정상 JSON + 올바른 채널 → convertAndSendToUser 호출 검증
    @Test
    void handleMessage_validInput_sendsToUser() throws Exception {
        String json = "{\"notificationId\":10}";
        given(objectMapper.readValue(eq(json), eq(NotificationResponse.class))).willReturn(RESPONSE);

        service.handleMessage(json, CHANNEL);

        verify(messagingTemplate).convertAndSendToUser(
                eq(String.valueOf(USER_ID)), eq(STOMP_QUEUE), eq(RESPONSE));
    }

    // handleMessage() — 잘못된 채널 prefix → convertAndSendToUser 미호출
    @Test
    void handleMessage_wrongChannelPrefix_skipsProcessing() {
        service.handleMessage("{}", "chat:123");

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    // handleMessage() — userId 없는 채널("notification:") → convertAndSendToUser 미호출
    @Test
    void handleMessage_blankUserId_skipsProcessing() {
        service.handleMessage("{}", "notification:");

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    // handleMessage() — JSON 파싱 실패 시 예외 미전파 (리스너 생존 보장)
    @Test
    void handleMessage_invalidJson_doesNotThrow() throws Exception {
        given(objectMapper.readValue(anyString(), eq(NotificationResponse.class)))
                .willThrow(mock(JacksonException.class));

        assertThatNoException().isThrownBy(() -> service.handleMessage("invalid-json", CHANNEL));
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    // handleMessage() — STOMP 브로드캐스트 실패 시 예외 미전파 + 메트릭 카운터 증가
    @Test
    void handleMessage_broadcastFailure_doesNotThrow_andIncrementsMetric() throws Exception {
        given(objectMapper.readValue(anyString(), eq(NotificationResponse.class))).willReturn(RESPONSE);
        given(meterRegistry.counter("notification.broadcast.failure.total")).willReturn(counter);
        willThrow(new RuntimeException("STOMP error"))
                .given(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        assertThatNoException().isThrownBy(() -> service.handleMessage("{}", CHANNEL));
        verify(counter).increment();
    }
}
