package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.common.response.ApiResponse;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN 권한 인증 흐름 검증 용도 임시 endpoint.
 *
 * <p>관리자 도메인 Epic이 진행되면 대시보드 등 정식 endpoint로 대체되며 본 컨트롤러는 제거된다.
 * 본 슬라이스(S5)는 URL 권한 매핑({@code /api/v1/admin/** = hasRole(ADMIN)})이 실제로 작동하는지
 * end-to-end로 검증하기 위한 최소 endpoint만 둔다.
 *
 * <p>제거 시점: 관리자 도메인 Epic의 초기 슬라이스 (임시 ping 정리)
 */
@RestController
@RequestMapping("/api/v1/admin/me")
public class AdminMeController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, Long>> ping(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(Map.of("userId", userId));
    }
}
