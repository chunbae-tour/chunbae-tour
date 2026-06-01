package com.chunbaetour.domain.admin.user.controller;

import com.chunbaetour.domain.admin.audit.AdminActionType;
import com.chunbaetour.domain.admin.audit.AdminTargetType;
import com.chunbaetour.domain.admin.audit.LogAdminAction;
import com.chunbaetour.domain.admin.user.dto.request.UserSuspendRequest;
import com.chunbaetour.domain.admin.user.dto.response.UserAdminDetailResponse;
import com.chunbaetour.domain.admin.user.dto.response.UserAdminListResponse;
import com.chunbaetour.domain.admin.user.service.AdminUserService;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 사용자 관리 API (KAN-180, Admin Epic KAN-177 S02).
 *
 * <p>{@code /api/v1/admin/**} 경로는 SecurityConfig에서 ADMIN 권한 필수.
 *
 * <p>정지/해제 endpoint에 {@link LogAdminAction}을 부착해 S01 audit 인프라가 자동 기록한다 —
 * {@code targetIdVar = "userId"}로 path 변수에서 결정적으로 대상 id를 추출(S01 리뷰 합의). 조회(GET) endpoint는
 * 상태 전이가 없어 audit 미부착.
 */
@Tag(name = "사용자 관리 (ADMIN)", description = "운영자 사용자 목록·상세·정지·해제 (/api/v1/admin/users/**)")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Validated
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "사용자 목록 조회 (keyword/status/role 필터 + cursor 페이징)")
    @GetMapping
    public ApiResponse<CursorPageResponse<UserAdminListResponse>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(adminUserService.getUsers(keyword, status, role, cursor, size));
    }

    @Operation(summary = "사용자 단건 상세 조회 (가입정보 + 정지상태 + 누적 신고)")
    @GetMapping("/{userId}")
    public ApiResponse<UserAdminDetailResponse> getUser(@PathVariable @Positive Long userId) {
        return ApiResponse.success(adminUserService.getUser(userId));
    }

    @Operation(summary = "사용자 정지 생성/갱신")
    @PostMapping("/{userId}/suspensions")
    @ResponseStatus(HttpStatus.CREATED)
    @LogAdminAction(actionType = AdminActionType.USER_SUSPEND,
            targetType = AdminTargetType.USER,
            targetIdVar = "userId")
    public ApiResponse<Void> suspend(
            @PathVariable @Positive Long userId,
            @Valid @RequestBody UserSuspendRequest request
    ) {
        adminUserService.suspend(userId, request.reason(), request.durationDays());
        return ApiResponse.success();
    }

    @Operation(summary = "사용자 정지 해제")
    @DeleteMapping("/{userId}/suspensions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @LogAdminAction(actionType = AdminActionType.USER_UNSUSPEND,
            targetType = AdminTargetType.USER,
            targetIdVar = "userId")
    public void unsuspend(@PathVariable @Positive Long userId) {
        adminUserService.unsuspend(userId);
    }
}
