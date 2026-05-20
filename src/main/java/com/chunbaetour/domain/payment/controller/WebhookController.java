package com.chunbaetour.domain.payment.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.payment.dto.request.WebhookPayload;
import com.chunbaetour.domain.payment.service.CallbackService;
import com.chunbaetour.domain.payment.service.WebhookVerifier;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
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

        WebhookPayload payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        if (payload.data() == null) {
            return ApiResponse.success();
        }
        if ("Transaction.Paid".equals(payload.type())) {
            callbackService.handleSuccess(payload.data().paymentId(), payload.data().txId());
        } else if ("Transaction.Failed".equals(payload.type())
                || "Transaction.Cancelled".equals(payload.type())) {
            callbackService.handleFail(payload.data().paymentId());
        }

        return ApiResponse.success();
    }
}
