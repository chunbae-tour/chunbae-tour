package com.chunbaetour.domain.chat.controller;

import com.chunbaetour.domain.chat.dto.request.ChatSendMessageRequest;
import com.chunbaetour.domain.chat.service.ChatMessageService;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    // STOMP 메시지 수신 → 멤버 검증·저장·Redis 발행 위임
    // 발행 경로: /pub/chat/rooms/{chatRoomId}/messages
    // 구독 경로: /sub/chat/rooms/{chatRoomId} (Redis Pub-Sub → SimpMessagingTemplate 브로드캐스트)
    @MessageMapping("/chat/rooms/{chatRoomId}/messages")
    public void sendMessage(
            @DestinationVariable Long chatRoomId,
            @Payload ChatSendMessageRequest request,
            Principal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        // principal.getName() = userId String — StompChannelInterceptor에서 설정
        final Long userId;
        try {
            userId = Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.ACCESS_TOKEN_INVALID);
        }
        chatMessageService.sendMessage(userId, chatRoomId, request);
    }
}
