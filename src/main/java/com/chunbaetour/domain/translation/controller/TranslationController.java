package com.chunbaetour.domain.translation.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.translation.dto.request.TranslationRequest;
import com.chunbaetour.domain.translation.dto.response.TranslationResponse;
import com.chunbaetour.domain.translation.service.TranslationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/translations")
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationService translationService;

    // 텍스트 번역 요청 — USER 전용, 외부 API 실패 시 COMMON_007 응답
    @PostMapping
    public ApiResponse<TranslationResponse> translate(@RequestBody @Valid TranslationRequest request) {
        return ApiResponse.success(translationService.translate(request.content(), request.targetLanguage()));
    }
}
