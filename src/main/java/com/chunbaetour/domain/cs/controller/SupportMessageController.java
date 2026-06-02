package com.chunbaetour.domain.cs.controller;

import com.chunbaetour.domain.cs.dto.response.StompErrorResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.cs.dto.request.SupportSendMessageRequest;
import com.chunbaetour.domain.cs.service.SupportMessageService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SupportMessageController {

    private final SupportMessageService supportMessageService;

    // STOMP 메시지 수신 → 권한 검증·저장·Redis 발행 위임
    // 발행 경로: /pub/support/rooms/{supportRoomId}/messages
    // 구독 경로: /sub/support/rooms/{supportRoomId}
    @MessageMapping("/support/rooms/{supportRoomId}/messages")
    public void sendMessage(
            @DestinationVariable Long supportRoomId,
            @Payload SupportSendMessageRequest request,
            Principal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        final Long userId;
        try {
            userId = Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.ACCESS_TOKEN_INVALID);
        }
        // principal authorities에서 ADMIN 여부 추출 — 클라이언트 전달값 신뢰 금지
        boolean isAdmin = principal instanceof UsernamePasswordAuthenticationToken auth &&
                auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        supportMessageService.sendMessage(userId, supportRoomId, isAdmin, request);
    }

    // @MessageMapping 처리 중 BusinessException — 발신 세션에만 에러 응답 전송
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public StompErrorResponse handleBusinessException(BusinessException e) {
        return new StompErrorResponse(e.getErrorCode().getCode(), e.getMessage());
    }

    // 예상치 못한 예외 — 내부 에러로 치환해 상세 정보 노출 차단
    @MessageExceptionHandler(Exception.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public StompErrorResponse handleException(Exception e) {
        log.error("Unexpected error in STOMP support message handling", e);
        return new StompErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }
}
