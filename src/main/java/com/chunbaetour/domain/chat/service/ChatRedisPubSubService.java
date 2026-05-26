package com.chunbaetour.domain.chat.service;

import com.chunbaetour.domain.chat.dto.response.ChatMessageResponse;
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
public class ChatRedisPubSubService {

    private static final String CHANNEL_PREFIX = "chat:";
    private static final String STOMP_TOPIC_PREFIX = "/sub/chat/rooms/";
    private static final String METRIC_BROADCAST_FAILURE = "chat.broadcast.failure.total";
    private static final String METRIC_SERIALIZE_FAILURE = "chat.serialize.failure.total";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    // SimpMessagingTemplate은 WebSocketConfig 초기화 이후 준비됨 — 순환 의존성 방지
    @Lazy
    private final SimpMessagingTemplate messagingTemplate;
    private final MeterRegistry meterRegistry;

    // 메시지 저장 후 Redis 채널 발행 — 모든 서버 인스턴스가 구독 중
    public void publish(Long chatRoomId, ChatMessageResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + chatRoomId, json);
        } catch (JacksonException e) {
            // afterCommit 컨텍스트라 throw해도 클라이언트 도달 불가 — 메트릭 알람으로 처리
            log.error("ChatMessageResponse 직렬화 실패. chatRoomId={}", chatRoomId, e);
            meterRegistry.counter(METRIC_SERIALIZE_FAILURE).increment();
        }
    }

    // Redis 구독 콜백 — chat:* 패턴 채널 메시지 수신 → STOMP 토픽으로 브로드캐스트
    public void handleMessage(String message, String channel) {
        ChatMessageResponse response;
        try {
            response = objectMapper.readValue(message, ChatMessageResponse.class);
        } catch (JacksonException e) {
            // corrupted message — 단일 메시지 손실, listener 유지
            log.warn("Redis 메시지 JSON 파싱 실패. channel={}", channel, e);
            return;
        }
        String chatRoomId = channel.substring(CHANNEL_PREFIX.length());
        try {
            messagingTemplate.convertAndSend(STOMP_TOPIC_PREFIX + chatRoomId, response);
        } catch (Exception e) {
            // STOMP 브로드캐스트 실패 — warn과 달리 error로 운영 알람 대상
            log.error("STOMP 브로드캐스트 실패. chatRoomId={}", chatRoomId, e);
            meterRegistry.counter(METRIC_BROADCAST_FAILURE).increment();
        }
    }
}
