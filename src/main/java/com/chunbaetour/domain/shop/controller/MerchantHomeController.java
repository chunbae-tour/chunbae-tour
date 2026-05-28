package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.chunbaetour.domain.shop.dto.response.MerchantHomeResponse;
import com.chunbaetour.domain.shop.service.MerchantHomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상인 홈 대시보드 API.
 * 오늘 매출 합계와 최근 QR 결제 목록을 조회한다.
 */
@RestController
@RequestMapping("/api/v1/merchants/me/home")
@RequiredArgsConstructor
public class MerchantHomeController {

    private final MerchantHomeService merchantHomeService;

    /**
     * 내 상인 홈 대시보드를 조회한다.
     *
     * @param userId 인증된 상인 사용자 ID
     * @return 오늘 매출 합계와 최근 결제 목록
     */
    @Operation(summary = "상인 홈 대시보드 조회")
    @GetMapping
    public ApiResponse<MerchantHomeResponse> getHome(@AuthenticationPrincipal Long userId) {
        // SecurityConfig가 인증을 보장하지만, null principal이 들어오는 비정상 상황은
        // AUTH_006으로 통일해 다른 /me 계열 컨트롤러와 응답 형태를 맞춘다.
        requireAuthenticated(userId);
        return ApiResponse.success(merchantHomeService.getHome(userId));
    }

    /**
     * SecurityContext에 userId가 채워지지 않은 비정상 상태를 표준 AUTH_006 에러로 변환.
     *
     * <p>본 컨트롤러는 인증 필수 endpoint이므로 userId null = 인증 실패와 동일하게 응답한다.
     * NPE 대신 도메인 에러로 통일해 클라이언트 응답 형식을 일관되게 유지한다.
     */
    private static void requireAuthenticated(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }
}
