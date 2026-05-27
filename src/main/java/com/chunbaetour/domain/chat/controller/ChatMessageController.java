package com.chunbaetour.domain.chat.controller;

import com.chunbaetour.domain.chat.dto.request.ChatSendMessageRequest;
import com.chunbaetour.domain.chat.dto.response.StompErrorResponse;
import com.chunbaetour.domain.chat.service.ChatMessageService;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
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
        // StompChannelInterceptor가 SEND 프레임 null principal을 차단하므로 정상 흐름에서 도달 불가
        // 인터셉터 설정 변경 대비 방어 코드
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

    // @MessageMapping 처리 중 BusinessException — 발신 세션에만 에러 응답 전송
    // broadcast=false: 동일 유저 다른 탭/기기로 에러 전파 차단
    // 클라이언트 구독 경로: /user/queue/errors
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public StompErrorResponse handleBusinessException(BusinessException e) {
        return new StompErrorResponse(e.getErrorCode().getCode(), e.getMessage());
    }

    // 예상치 못한 예외 — 내부 에러로 치환해 상세 정보 노출 차단
    @MessageExceptionHandler(Exception.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public StompErrorResponse handleException(Exception e) {
        return new StompErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }
}
