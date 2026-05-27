package com.chunbaetour.domain.notification.service;

import com.chunbaetour.domain.notification.dto.response.NotificationResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationRedisPubSubService {

    private static final String CHANNEL_PREFIX = "notification:";
    private static final String STOMP_QUEUE = "/queue/notifications";
    private static final String METRIC_BROADCAST_FAILURE = "notification.broadcast.failure.total";
    private static final String METRIC_SERIALIZE_FAILURE = "notification.serialize.failure.total";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    // SimpMessagingTemplate은 WebSocketConfig 초기화 이후 준비됨 — 순환 의존성 방지
    @Lazy
    private final SimpMessagingTemplate messagingTemplate;
    private final MeterRegistry meterRegistry;

    // 알림 저장 후 Redis 채널 발행 — 모든 서버 인스턴스가 구독 중
    public void publish(Long userId, NotificationResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + userId, json);
        } catch (JacksonException e) {
            log.error("NotificationResponse 직렬화 실패. userId={}", userId, e);
            meterRegistry.counter(METRIC_SERIALIZE_FAILURE).increment();
        }
    }

    // Redis 구독 콜백 — notification:* 패턴 채널 메시지 수신 → 개인 STOMP 큐로 전송
    public void handleMessage(String message, String channel) {
        if (!channel.startsWith(CHANNEL_PREFIX)) {
            log.warn("예상하지 못한 Redis 채널입니다. channel={}", channel);
            return;
        }
        NotificationResponse response;
        try {
            response = objectMapper.readValue(message, NotificationResponse.class);
        } catch (JacksonException e) {
            log.warn("Redis 메시지 JSON 파싱 실패. channel={}", channel, e);
            return;
        }
        String userId = channel.substring(CHANNEL_PREFIX.length());
        try {
            messagingTemplate.convertAndSendToUser(userId, STOMP_QUEUE, response);
        } catch (Exception e) {
            log.error("STOMP 개인 알림 Push 실패. userId={}", userId, e);
            meterRegistry.counter(METRIC_BROADCAST_FAILURE).increment();
        }
    }
}
