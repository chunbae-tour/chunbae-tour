package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.UserMeResponse;
import com.chunbaetour.domain.common.response.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * USER 마이페이지 endpoint.
 *
 * <ul>
 *   <li>{@code GET /api/v1/users/me} — 본인 정보 조회 (Epic A S1, KAN-63)</li>
 *   <li>{@code GET /api/v1/users/me/ping} — 인증 흐름 검증용 임시 endpoint (S2~S5).
 *       Epic A S4 정리 슬라이스에서 제거 예정.</li>
 * </ul>
 *
 * <p>URL 권한:
 * <ul>
 *   <li>{@code SecurityConfig}가 {@code /api/v1/users/**} = {@code hasRole(USER)}로 매핑.
 *       MERCHANT/ADMIN 토큰으로 호출 시 {@code AUTH_007}로 거부됨.</li>
 *   <li>비인증 호출은 {@code RestAuthenticationEntryPoint}가 {@code AUTH_006} 응답.</li>
 * </ul>
 *
 * <p>본인 식별: URL에 userId를 노출하지 않고 {@code @AuthenticationPrincipal Long userId}로 SecurityContext에서
 * 추출. 타인 정보 조회 차단의 핵심 — URL 조작으로 다른 사용자 정보를 절대 볼 수 없게 한다.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserMeController {

    private final UserMeService userMeService;

    /**
     * 본인 정보 조회 (Epic A S1).
     *
     * @param userId SecurityContext에 저장된 본인 ID (JwtAuthenticationFilter가 채움)
     * @return 본인 마이페이지 정보 (민감 필드 제외)
     */
    @GetMapping
    public ApiResponse<UserMeResponse> getMe(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(userMeService.getMe(userId));
    }

    /**
     * 인증 흐름 검증용 임시 endpoint. Epic A S4 정리 슬라이스에서 제거 예정.
     *
     * <p>S2~S5의 통합 테스트가 본 endpoint를 호출 중이므로 본 슬라이스에서는 유지한다 (회귀 방지).
     * 모든 통합 테스트가 {@code GET /me}로 마이그레이션 완료된 후 일괄 제거한다.
     */
    @GetMapping("/ping")
    public ApiResponse<Map<String, Long>> ping(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(Map.of("userId", userId));
    }
}
