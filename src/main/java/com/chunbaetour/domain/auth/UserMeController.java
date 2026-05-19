package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.common.response.ApiResponse;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 인증 흐름 검증 용도 임시 endpoint. 마이페이지 PRD (S4) 완료 시 제거.
@RestController
@RequestMapping("/api/v1/users/me")
public class UserMeController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, Long>> ping(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(Map.of("userId", userId));
    }
}
