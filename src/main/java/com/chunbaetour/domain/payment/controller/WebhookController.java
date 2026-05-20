package com.chunbaetour.domain.payment.controller;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.payment.dto.request.WebhookPayload;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.service.CallbackService;
import com.chunbaetour.domain.payment.service.WebhookVerifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookVerifier webhookVerifier;
    private final CallbackService callbackService;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhook")
    public ApiResponse<Void> webhook(
            @RequestHeader("webhook-id") String webhookId,
            @RequestHeader("webhook-signature") String signature,
            @RequestHeader("webhook-timestamp") String timestamp,
            @RequestBody String rawBody
    ) {
        webhookVerifier.verify(webhookId, signature, timestamp, rawBody);

        // 서명 검증 + 파싱 + 위임만
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (JsonProcessingException e) {
            throw new PaymentException(ErrorCode.INVALID_REQUEST);
        }
        callbackService.handle(payload);

        return ApiResponse.success();
    }
}
