package com.chunbaetour.domain.place.controller;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.ratelimit.RateLimitDecision;
import com.chunbaetour.domain.common.ratelimit.RateLimitPolicy;
import com.chunbaetour.domain.common.ratelimit.RateLimiter;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.place.dto.Coord;
import com.chunbaetour.domain.place.dto.request.DirectionRequest;
import com.chunbaetour.domain.place.dto.response.DirectionResponse;
import com.chunbaetour.domain.place.service.DirectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "길찾기", description = "카카오맵 길찾기 URL 생성 (/api/v1/directions)")
@RestController
// [MEDIUM] 이 API는 카카오맵 길찾기 링크 생성을 제공하므로 비회원(permitAll) 접근을 의도함
@RequestMapping("/api/v1/directions")
@RequiredArgsConstructor
public class DirectionController {

    private final DirectionService directionService;
    private final RateLimiter rateLimiter;
    
    // [MEDIUM] 악의적인 반복 호출에 의한 카카오 유료 API 쿼터 고갈을 막기 위한 Rate Limit 정책 (분당 10회)
    private static final RateLimitPolicy RATE_LIMIT_POLICY = new RateLimitPolicy(10, Duration.ofMinutes(1));

    @Operation(summary = "카카오맵 길찾기 URL 생성")
    @GetMapping
    public ResponseEntity<ApiResponse<DirectionResponse>> getDirections(
            @Valid @ModelAttribute DirectionRequest request,
            HttpServletRequest servletRequest,
            jakarta.servlet.http.HttpServletResponse servletResponse
    ) {
        // Rate Limiting 적용 (클라이언트 IP 기반)
        String clientIp = servletRequest.getRemoteAddr();
        String rateLimitKey = "ratelimit:directions:" + clientIp;
        RateLimitDecision decision = rateLimiter.tryConsume(rateLimitKey, RATE_LIMIT_POLICY);
        
        if (!decision.allowed()) {
            servletResponse.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfter().getSeconds()));
            throw new com.chunbaetour.domain.common.error.BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        // [MEDIUM] 파라미터 순서 뒤바뀜 실수 원천 차단을 위해 명확한 타입(Coord)으로 객체화 후 전달
        Coord origin = new Coord(request.originLat(), request.originLng());
        Coord dest = new Coord(request.destLat(), request.destLng());

        String redirectUrl = directionService.buildKakaoMapUrl(origin, dest);
        
        return ResponseEntity.ok()
                .header("X-RateLimit-Remaining", String.valueOf(decision.remaining()))
                .body(ApiResponse.success(DirectionResponse.of("KAKAO", redirectUrl)));
    }
}
