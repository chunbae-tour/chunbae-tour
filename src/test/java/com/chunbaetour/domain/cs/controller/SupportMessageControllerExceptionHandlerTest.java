package com.chunbaetour.domain.cs.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.cs.dto.response.StompErrorResponse;
import com.chunbaetour.domain.cs.service.SupportMessageService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.annotation.SendToUser;

@ExtendWith(MockitoExtension.class)
class SupportMessageControllerExceptionHandlerTest {

    @Mock private SupportMessageService supportMessageService;
    @InjectMocks private SupportMessageController supportMessageController;

    // BusinessException — 에러코드·메시지 그대로 반환
    @Test
    void handleBusinessException_returns_errorCode_and_message() {
        BusinessException ex = new BusinessException(ErrorCode.SUPPORT_ROOM_FORBIDDEN);

        StompErrorResponse response = supportMessageController.handleBusinessException(ex);

        assertThat(response.errorCode()).isEqualTo(ErrorCode.SUPPORT_ROOM_FORBIDDEN.getCode());
        assertThat(response.message()).isEqualTo(ErrorCode.SUPPORT_ROOM_FORBIDDEN.getMessage());
    }

    // TOO_MANY_REQUESTS — rate limit 에러도 그대로 반환
    @Test
    void handleBusinessException_rate_limit_returns_correct_errorCode() {
        BusinessException ex = new BusinessException(ErrorCode.TOO_MANY_REQUESTS);

        StompErrorResponse response = supportMessageController.handleBusinessException(ex);

        assertThat(response.errorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getCode());
    }

    // Exception — INTERNAL_SERVER_ERROR 고정 반환 (내부 정보 노출 차단)
    @Test
    void handleException_returns_INTERNAL_SERVER_ERROR() {
        StompErrorResponse response = supportMessageController.handleException(new RuntimeException("DB 연결 실패"));

        assertThat(response.errorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
        assertThat(response.message()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    // Exception — 원본 예외 메시지 클라이언트에 노출 안 됨
    @Test
    void handleException_does_not_expose_original_message() {
        StompErrorResponse response = supportMessageController.handleException(new NullPointerException("internal secret"));

        assertThat(response.message()).doesNotContain("internal secret");
    }

    // @SendToUser(broadcast=false) — BusinessException 핸들러 어노테이션 검증
    @Test
    void handleBusinessException_sendToUser_annotation_routesToQueueErrors_broadcastFalse() throws NoSuchMethodException {
        Method method = SupportMessageController.class.getMethod("handleBusinessException", BusinessException.class);
        SendToUser annotation = method.getAnnotation(SendToUser.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("/queue/errors");
        assertThat(annotation.broadcast()).isFalse();
    }

    // @SendToUser(broadcast=false) — Exception 핸들러도 동일 설정 검증
    @Test
    void handleException_sendToUser_annotation_routesToQueueErrors_broadcastFalse() throws NoSuchMethodException {
        Method method = SupportMessageController.class.getMethod("handleException", Exception.class);
        SendToUser annotation = method.getAnnotation(SendToUser.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("/queue/errors");
        assertThat(annotation.broadcast()).isFalse();
    }
}
