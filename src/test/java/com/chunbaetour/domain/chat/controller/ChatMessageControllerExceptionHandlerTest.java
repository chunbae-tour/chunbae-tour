package com.chunbaetour.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.chat.dto.response.StompErrorResponse;
import com.chunbaetour.domain.chat.service.ChatMessageService;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class ChatMessageControllerExceptionHandlerTest {

    @Mock
    private ChatMessageService chatMessageService;

    @InjectMocks
    private ChatMessageController chatMessageController;

    // BusinessException — 에러코드·메시지 그대로 반환
    @Test
    void handleBusinessException_returns_errorCode_and_message() {
        BusinessException ex = new BusinessException(ErrorCode.CHAT_NOT_JOINED);

        StompErrorResponse response = chatMessageController.handleBusinessException(ex);

        assertThat(response.errorCode()).isEqualTo(ErrorCode.CHAT_NOT_JOINED.getCode());
        assertThat(response.message()).isEqualTo(ErrorCode.CHAT_NOT_JOINED.getMessage());
    }

    // BusinessException — 다른 에러코드도 그대로 반환 (TOO_MANY_REQUESTS)
    @Test
    void handleBusinessException_rate_limit_returns_correct_errorCode() {
        BusinessException ex = new BusinessException(ErrorCode.TOO_MANY_REQUESTS);

        StompErrorResponse response = chatMessageController.handleBusinessException(ex);

        assertThat(response.errorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getCode());
        assertThat(response.message()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getMessage());
    }

    // Exception — INTERNAL_SERVER_ERROR 고정 반환 (내부 정보 노출 차단)
    @Test
    void handleException_returns_INTERNAL_SERVER_ERROR() {
        Exception ex = new RuntimeException("DB connection lost");

        StompErrorResponse response = chatMessageController.handleException(ex);

        assertThat(response.errorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
        assertThat(response.message()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    // Exception — 원본 예외 메시지 클라이언트에 노출 안 됨
    @Test
    void handleException_does_not_expose_original_message() {
        Exception ex = new NullPointerException("internal secret");

        StompErrorResponse response = chatMessageController.handleException(ex);

        assertThat(response.message()).doesNotContain("internal secret");
    }
}
