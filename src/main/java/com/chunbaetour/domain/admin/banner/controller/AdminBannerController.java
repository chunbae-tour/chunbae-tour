package com.chunbaetour.domain.admin.banner.controller;

import com.chunbaetour.domain.admin.audit.AdminActionType;
import com.chunbaetour.domain.admin.audit.AdminTargetType;
import com.chunbaetour.domain.admin.audit.LogAdminAction;
import com.chunbaetour.domain.admin.banner.dto.request.AdminBannerCreateRequest;
import com.chunbaetour.domain.admin.banner.dto.request.AdminBannerUpdateRequest;
import com.chunbaetour.domain.admin.banner.dto.response.AdminBannerDetailResponse;
import com.chunbaetour.domain.admin.banner.dto.response.AdminBannerListResponse;
import com.chunbaetour.domain.admin.banner.service.AdminBannerService;
import com.chunbaetour.domain.banner.type.BannerStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 배너 관리 API (Admin Epic KAN-177 S09, KAN-216).
 *
 * <p>{@code /api/v1/admin/**} 경로는 SecurityConfig에서 ADMIN 권한 필수 — 클래스 {@code @PreAuthorize}로 명시
 * (S02/S04/S05/S07 컨벤션 유지).
 *
 * <p>CUD endpoint에 {@link LogAdminAction}을 부착해 S01 audit 인프라가 자동 기록한다. PATCH/DELETE는
 * {@code targetIdVar = "bannerId"}로 path 변수에서 결정적으로 대상 id를 추출한다. POST(등록)는 생성 전 id가
 * 없어 {@code returnIdField = "id"}로 응답 본문(생성된 배너 id)에서 targetId를 추출한다(S07 CREATE 공통).
 * 목록(GET) endpoint는 상태 전이가 없어 audit 미부착.
 */
@Tag(name = "배너 관리 (ADMIN)", description = "운영자 추천 배너 목록·등록·수정·삭제 (/api/v1/admin/banners/**)")
@RestController
@RequestMapping("/api/v1/admin/banners")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminBannerController {

    private final AdminBannerService adminBannerService;

    @Operation(summary = "배너 목록 조회 (status 필터 + priority/id cursor 페이징)")
    @GetMapping
    public ApiResponse<CursorPageResponse<AdminBannerListResponse>> getBanners(
            @RequestParam(required = false) BannerStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(adminBannerService.getBanners(status, cursor, size));
    }

    @Operation(summary = "배너 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @LogAdminAction(actionType = AdminActionType.BANNER_CREATE,
            targetType = AdminTargetType.BANNER,
            returnIdField = "id")
    public ApiResponse<AdminBannerDetailResponse> createBanner(
            @Valid @RequestBody AdminBannerCreateRequest request
    ) {
        return ApiResponse.success(adminBannerService.createBanner(request));
    }

    @Operation(summary = "배너 수정 (partial update)")
    @PatchMapping("/{bannerId}")
    @LogAdminAction(actionType = AdminActionType.BANNER_UPDATE,
            targetType = AdminTargetType.BANNER,
            targetIdVar = "bannerId")
    public ApiResponse<AdminBannerDetailResponse> updateBanner(
            @PathVariable @Positive Long bannerId,
            @Valid @RequestBody AdminBannerUpdateRequest request
    ) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return ApiResponse.success(adminBannerService.updateBanner(bannerId, request));
    }

    @Operation(summary = "배너 삭제 (soft delete)")
    @DeleteMapping("/{bannerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @LogAdminAction(actionType = AdminActionType.BANNER_DELETE,
            targetType = AdminTargetType.BANNER,
            targetIdVar = "bannerId")
    public void deleteBanner(@PathVariable @Positive Long bannerId) {
        adminBannerService.deleteBanner(bannerId);
    }
}
