package com.chunbaetour.domain.admin.user.controller;

import com.chunbaetour.domain.admin.audit.AdminActionType;
import com.chunbaetour.domain.admin.audit.AdminTargetType;
import com.chunbaetour.domain.admin.audit.LogAdminAction;
import com.chunbaetour.domain.admin.user.dto.response.UserSanctionResponse;
import com.chunbaetour.domain.admin.user.service.AdminSanctionService;
import com.chunbaetour.domain.auth.security.JwtAuthenticationFilter;
import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 제재 이력 관리 API.
 *
 * <p>블랙리스트(system sanction) 조회·조기 해제 기능 제공.
 * 수동 정지({@code /admin/users/{userId}/suspensions})와 별개로 신고 시스템의 자동 제재를 관리한다.
 */
@Tag(name = "제재 관리 (ADMIN)", description = "운영자 제재 이력 조회·해제 (/api/v1/admin/users/{userId}/sanctions)")
@RestController
@RequestMapping("/api/v1/admin/users/{userId}/sanctions")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminSanctionController {

    private final AdminSanctionService adminSanctionService;

    @Operation(summary = "제재 이력 조회 (최신순)")
    @GetMapping
    public ApiResponse<List<UserSanctionResponse>> getSanctions(
            @PathVariable @Positive Long userId
    ) {
        return ApiResponse.success(adminSanctionService.getSanctions(userId));
    }

    @Operation(summary = "제재 조기 해제")
    @DeleteMapping("/{sanctionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @LogAdminAction(actionType = AdminActionType.SANCTION_RELEASE,
            targetType = AdminTargetType.USER,
            targetIdVar = "userId")
    public void releaseSanction(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long sanctionId,
            HttpServletRequest request
    ) {
        AccessClaims claims = (AccessClaims) request.getAttribute(
                JwtAuthenticationFilter.REQUEST_ATTR_ACCESS_CLAIMS);
        adminSanctionService.releaseSanction(sanctionId, claims.userId());
    }
}
