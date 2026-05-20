package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.common.response.ApiResponse;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MERCHANT 권한 인증 흐름 검증 용도 임시 endpoint.
 *
 * <p>마이페이지 Epic이 진행되면 정식 {@code GET /api/v1/merchants/me} 등으로 대체되며 본 컨트롤러는 제거된다.
 * 본 슬라이스(S5)는 URL 권한 매핑({@code /api/v1/merchants/** = hasRole(MERCHANT)})이 실제로 작동하는지
 * end-to-end로 검증하기 위한 최소 endpoint만 둔다.
 *
 * <p>제거 시점: 마이페이지 Epic의 S4 (임시 ping 정리 슬라이스)
 */
@RestController
@RequestMapping("/api/v1/merchants/me")
public class MerchantMeController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, Long>> ping(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(Map.of("userId", userId));
    }
}
