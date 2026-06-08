package com.chunbaetour.domain.chat.config;

import com.chunbaetour.domain.auth.security.CorsProperties;
import com.chunbaetour.domain.chat.service.ChatRedisPubSubService;
import com.chunbaetour.domain.cs.service.SupportRedisPubSubService;
import com.chunbaetour.domain.notification.service.NotificationRedisPubSubService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompChannelInterceptor stompChannelInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // /ws-stomp: SockJS fallback 포함 — 브라우저 WebSocket 미지원 환경 대비
        registry.addEndpoint("/ws-stomp")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/pub");
        // /sub: 채팅 브로드캐스트 토픽, /queue: @SendToUser 개인 에러 큐
        registry.enableSimpleBroker("/sub", "/queue");
    }

    // JWT 검증 인터셉터 — STOMP CONNECT 프레임에만 적용
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompChannelInterceptor);
    }

    // chat:* + support:* 패턴 Redis 채널 구독 — SupportRedisPubSubService 공유 (빈 최소화)
    @Bean
    public RedisMessageListenerContainer chatRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatRedisPubSubService chatRedisPubSubService,
            SupportRedisPubSubService supportRedisPubSubService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // MessageListener 구현체 직접 등록 — MessageListenerAdapter 경유 시 channel에 패턴이 전달되는 버그 수정
        container.addMessageListener(chatRedisPubSubService, new PatternTopic("chat:*"));
        container.addMessageListener(supportRedisPubSubService, new PatternTopic("support:*"));
        return container;
    }

    // notification:* 패턴 Redis 채널 구독 — 다중 서버 환경에서 개인 알림 Push 동기화
    @Bean
    public RedisMessageListenerContainer notificationRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            NotificationRedisPubSubService notificationRedisPubSubService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(notificationRedisPubSubService, new PatternTopic("notification:*"));
        return container;
    }
}
