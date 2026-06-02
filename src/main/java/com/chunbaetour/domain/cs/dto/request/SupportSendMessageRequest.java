package com.chunbaetour.domain.cs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// STOMP 메시지 전송 요청 DTO — TEXT 타입 전용 (IMAGE/FILE은 S3 연동 후 별도 티켓)
public record SupportSendMessageRequest(
        @NotBlank @Size(max = 1000) String content
) {}
