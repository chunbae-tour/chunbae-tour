package com.chunbaetour.domain.cs.service;

import com.chunbaetour.domain.cs.dto.response.SupportMessageResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class SupportRedisPubSubService implements MessageListener {

    private static final String CHANNEL_PREFIX = "support:";
    private static final String STOMP_TOPIC_PREFIX = "/sub/support/rooms/";
    static final String METRIC_BROADCAST_FAILURE = "support.broadcast.failure.total";
    static final String METRIC_SERIALIZE_FAILURE = "support.serialize.failure.total";
    static final String METRIC_PUBLISH_FAILURE = "support.publish.failure.total";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    // SimpMessagingTemplate은 WebSocketConfig 초기화 이후 준비됨 — 순환 의존성 방지
    @Lazy
    private final SimpMessagingTemplate messagingTemplate;
    private final MeterRegistry meterRegistry;

    // 메시지 저장 후 Redis 채널 발행 — 모든 서버 인스턴스가 구독 중
    public void publish(Long supportRoomId, SupportMessageResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + supportRoomId, json);
        } catch (JacksonException e) {
            log.error("SupportMessageResponse 직렬화 실패. supportRoomId={}", supportRoomId, e);
            meterRegistry.counter(METRIC_SERIALIZE_FAILURE).increment();
        } catch (Exception e) {
            log.error("Redis 채널 발행 실패. supportRoomId={}", supportRoomId, e);
            meterRegistry.counter(METRIC_PUBLISH_FAILURE).increment();
        }
    }

    // MessageListener 구현 — PatternTopic 구독 시 message.getChannel()로 실제 채널 추출
    @Override
    public void onMessage(Message message, byte[] pattern) {
        byte[] channelBytes = message.getChannel();
        byte[] bodyBytes = message.getBody();
        if (channelBytes == null) {
            log.warn("Redis 메시지에 채널 정보가 없습니다. body={}",
                    bodyBytes != null ? new String(bodyBytes, StandardCharsets.UTF_8) : "(null)");
            return;
        }
        if (bodyBytes == null || bodyBytes.length == 0) {
            log.warn("Redis 메시지 body가 없습니다. channel={}", new String(channelBytes, StandardCharsets.UTF_8));
            return;
        }
        String channel = new String(channelBytes, StandardCharsets.UTF_8);
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        handleMessage(body, channel);
    }

    // Redis 구독 콜백 — support:* 채널 메시지 수신 → STOMP 토픽으로 브로드캐스트
    // channel 파라미터에 반드시 실제 채널명("support:123")이 전달되어야 함 — onMessage()가 보장
    // package-private: 외부 진입점은 onMessage()뿐, 테스트에서만 직접 호출
    void handleMessage(String message, String channel) {
        if (!channel.startsWith(CHANNEL_PREFIX)) {
            log.warn("예상하지 못한 Redis 채널입니다. channel={}", channel);
            return;
        }
        SupportMessageResponse response;
        try {
            response = objectMapper.readValue(message, SupportMessageResponse.class);
        } catch (JacksonException e) {
            log.warn("Redis 메시지 JSON 파싱 실패. channel={}", channel, e);
            return;
        }
        String roomId = channel.substring(CHANNEL_PREFIX.length());
        if (roomId.isBlank()) {
            log.warn("supportRoomId가 없는 Redis 채널입니다. channel={}", channel);
            return;
        }
        try {
            messagingTemplate.convertAndSend(STOMP_TOPIC_PREFIX + roomId, response);
        } catch (Exception e) {
            log.error("STOMP 브로드캐스트 실패. supportRoomId={}", roomId, e);
            meterRegistry.counter(METRIC_BROADCAST_FAILURE).increment();
        }
    }
}
