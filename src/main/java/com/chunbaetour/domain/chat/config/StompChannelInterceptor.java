package com.chunbaetour.domain.chat.config;

import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.jsonwebtoken.JwtException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

// STOMP CONNECT: JWT 검증, SUBSCRIBE: 채팅방 멤버십 인가
@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CHAT_ROOM_TOPIC_PREFIX = "/sub/chat/rooms/";
    private static final List<ChatMemberState> ACTIVE_STATES =
            List.of(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);

    private final TokenIssuer tokenIssuer;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return handleConnect(message, accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return handleSubscribe(message, accessor);
        }

        return message;
    }

    // CONNECT — JWT 검증 후 principal 세팅
    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        AccessClaims claims;
        try {
            claims = tokenIssuer.verifyAccess(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.ACCESS_TOKEN_INVALID);
        }

        // principal.getName() = userId String — ChatMessageController에서 Long.parseLong으로 추출
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                String.valueOf(claims.userId()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()))
        );
        accessor.setUser(auth);
        return message;
    }

    // SUBSCRIBE — 채팅방 구독 시 ACTIVE 멤버 검증 (비멤버·강퇴·퇴장 차단)
    private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(CHAT_ROOM_TOPIC_PREFIX)) {
            return message;
        }

        String roomIdStr = destination.substring(CHAT_ROOM_TOPIC_PREFIX.length());
        Long chatRoomId;
        try {
            chatRoomId = Long.parseLong(roomIdStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Long userId = Long.parseLong(accessor.getUser().getName());
        boolean isMember = chatRoomMemberRepository
                .existsByChatRoomIdAndUserIdAndMemberStateIn(chatRoomId, userId, ACTIVE_STATES);
        if (!isMember) {
            throw new BusinessException(ErrorCode.CHAT_NOT_JOINED);
        }

        return message;
    }
}
