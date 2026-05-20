package com.chunbaetour.domain.place.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.place.dto.request.DirectionRequest;
import com.chunbaetour.domain.place.dto.response.DirectionResponse;
import com.chunbaetour.domain.place.service.DirectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/directions")
@RequiredArgsConstructor
public class DirectionController {

    private final DirectionService directionService;

    @GetMapping
    public ApiResponse<DirectionResponse> getDirections(@Valid @ModelAttribute DirectionRequest request) {
        String redirectUrl = directionService.buildKakaoMapUrl(
                request.originLat(),
                request.originLng(),
                request.destLat(),
                request.destLng()
        );
        return ApiResponse.success(DirectionResponse.of("KAKAO", redirectUrl));
    }
}
