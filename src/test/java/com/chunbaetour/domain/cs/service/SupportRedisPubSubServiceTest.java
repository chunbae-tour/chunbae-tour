package com.chunbaetour.domain.cs.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.cs.dto.response.SupportMessageResponse;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SupportRedisPubSubServiceTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter counter;

    private SupportRedisPubSubService service;

    private static final Long ROOM_ID = 456L;
    private static final String ACTUAL_CHANNEL = "support:" + ROOM_ID;
    private static final String PATTERN = "support:*";

    private static final SupportMessageResponse RESPONSE = new SupportMessageResponse(
            1L, 10L, SupportSenderRole.CUSTOMER, SupportMessageType.TEXT,
            "문의드립니다", null, null, null, LocalDateTime.of(2024, 1, 1, 0, 0));

    @BeforeEach
    void setUp() {
        service = new SupportRedisPubSubService(
                stringRedisTemplate, objectMapper, messagingTemplate, meterRegistry);
    }

    // onMessage() — PatternTopic 경유 시 실제 채널("support:456")을 message.getChannel()로 추출해 브로드캐스트
    // 핵심 회귀 가드: MessageListenerAdapter가 패턴("support:*")을 channel로 넘기던 버그 수정 검증
    @Test
    void onMessage_extractsActualChannelFromMessage_notPattern() throws Exception {
        Message message = mock(Message.class);
        given(message.getChannel()).willReturn(ACTUAL_CHANNEL.getBytes(StandardCharsets.UTF_8));
        given(message.getBody()).willReturn("{}".getBytes(StandardCharsets.UTF_8));
        given(objectMapper.readValue(eq("{}"), eq(SupportMessageResponse.class))).willReturn(RESPONSE);

        service.onMessage(message, PATTERN.getBytes(StandardCharsets.UTF_8));

        verify(messagingTemplate).convertAndSend(eq("/sub/support/rooms/" + ROOM_ID), eq(RESPONSE));
    }

    // publish() — support:{roomId} 채널로 직렬화된 JSON 발행 검증
    @Test
    void publish_sendsSerializedJsonToRedisChannel() throws Exception {
        String json = "{\"supportRoomId\":456}";
        given(objectMapper.writeValueAsString(any())).willReturn(json);

        service.publish(ROOM_ID, RESPONSE);

        verify(stringRedisTemplate).convertAndSend(ACTUAL_CHANNEL, json);
    }

    // handleMessage() — 올바른 채널 + 정상 JSON → STOMP 브로드캐스트 호출 검증
    @Test
    void handleMessage_validInput_broadcastsToStompTopic() throws Exception {
        String json = "{}";
        given(objectMapper.readValue(eq(json), eq(SupportMessageResponse.class))).willReturn(RESPONSE);

        service.handleMessage(json, ACTUAL_CHANNEL);

        verify(messagingTemplate).convertAndSend(eq("/sub/support/rooms/" + ROOM_ID), eq(RESPONSE));
    }

    // handleMessage() — 잘못된 채널 prefix → 파싱·전송 모두 미호출
    @Test
    void handleMessage_wrongChannelPrefix_skipsProcessing() throws Exception {
        service.handleMessage("{}", "chat:123");

        verify(objectMapper, never()).readValue(anyString(), eq(SupportMessageResponse.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) any());
    }

    // handleMessage() — JSON 파싱 실패 시 예외 미전파 (리스너 생존 보장)
    @Test
    void handleMessage_invalidJson_doesNotThrow() throws Exception {
        given(objectMapper.readValue(anyString(), eq(SupportMessageResponse.class)))
                .willThrow(mock(JacksonException.class));

        assertThatNoException().isThrownBy(() -> service.handleMessage("invalid", ACTUAL_CHANNEL));
        verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) any());
    }

    // publish() — Redis 발행 실패 시 예외 미전파 + support.publish.failure.total 카운터 증가
    @Test
    void publish_redisFailure_doesNotThrow_andIncrementsMetric() throws Exception {
        given(meterRegistry.counter(SupportRedisPubSubService.METRIC_PUBLISH_FAILURE)).willReturn(counter);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        willThrow(new RuntimeException("Redis connection refused"))
                .given(stringRedisTemplate).convertAndSend(anyString(), anyString());

        assertThatNoException().isThrownBy(() -> service.publish(ROOM_ID, RESPONSE));
        verify(counter).increment();
    }

    // handleMessage() — roomId 없는 채널("support:") → 전송 미호출
    @Test
    void handleMessage_blankRoomId_skipsProcessing() throws Exception {
        given(objectMapper.readValue(anyString(), eq(SupportMessageResponse.class))).willReturn(RESPONSE);

        service.handleMessage("{}", "support:");

        verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) any());
    }

    // handleMessage() — STOMP 브로드캐스트 실패 시 예외 미전파 + 메트릭 카운터 증가
    @Test
    void handleMessage_broadcastFailure_doesNotThrow_andIncrementsMetric() throws Exception {
        given(objectMapper.readValue(anyString(), eq(SupportMessageResponse.class))).willReturn(RESPONSE);
        given(meterRegistry.counter(SupportRedisPubSubService.METRIC_BROADCAST_FAILURE)).willReturn(counter);
        willThrow(new RuntimeException("STOMP error"))
                .given(messagingTemplate).convertAndSend(anyString(), (Object) any());

        assertThatNoException().isThrownBy(() -> service.handleMessage("{}", ACTUAL_CHANNEL));
        verify(counter).increment();
    }
}
