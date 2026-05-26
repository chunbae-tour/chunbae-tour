package com.chunbaetour.domain.auth.support;

import com.chunbaetour.domain.common.response.ApiResponse;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>테스트 전용 fixture controller</b> — 운영 코드 아님. {@code src/test}에 위치.
 *
 * <p>SecurityConfig의 role 권한 매핑({@code /api/v1/users/** = USER}, {@code /api/v1/merchants/** = MERCHANT},
 * {@code /api/v1/admin/** = ADMIN})이 endpoint에 도달 전에 정확히 작동하는지 검증하기 위한
 * 최소 endpoint 3개. 본 클래스는 {@code @SpringBootTest} 컴포넌트 스캔 범위에 포함되어 통합 테스트 시점에만 빈으로 등록된다.
 *
 * <p>KAN-129 (Epic A S4) 임시 ping endpoint 정리 시 운영 컨트롤러를 제거하면서, role 매핑 검증을 위해
 * 운영 도메인 endpoint(ShopController, AdminRefundController 등)에 의존하면 시드 데이터/응답 분기에 결합도가
 * 높아진다. 본 fixture는 SecurityConfig 매핑 검증에만 집중 + 200 OK만 응답하도록 단순화.
 *
 * <p>응답 본문은 의미 없음 — role 매핑 통과 여부(200 vs AUTH_007 vs AUTH_006)만 검증.
 *
 * <p><b>중요</b>: 본 endpoint를 운영(src/main)으로 옮기지 말 것. 외부 노출 시 dead endpoint + 검증용 정보 노출 위험.
 */
@RestController
public class TestAuthFixtureController {

    @GetMapping("/api/v1/users/test-auth-fixture")
    public ApiResponse<Map<String, Long>> userFixture(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(Map.of("userId", userId));
    }

    @GetMapping("/api/v1/merchants/test-auth-fixture")
    public ApiResponse<Map<String, Long>> merchantFixture(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(Map.of("userId", userId));
    }

    @GetMapping("/api/v1/admin/test-auth-fixture")
    public ApiResponse<Map<String, Long>> adminFixture(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(Map.of("userId", userId));
    }
}
