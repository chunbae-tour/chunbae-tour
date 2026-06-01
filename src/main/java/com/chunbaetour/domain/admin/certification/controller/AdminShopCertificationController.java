package com.chunbaetour.domain.admin.certification.controller;

import com.chunbaetour.domain.admin.audit.AdminActionType;
import com.chunbaetour.domain.admin.audit.AdminTargetType;
import com.chunbaetour.domain.admin.audit.LogAdminAction;
import com.chunbaetour.domain.admin.certification.dto.request.CertificationCancelRequest;
import com.chunbaetour.domain.admin.certification.dto.request.CertificationRejectRequest;
import com.chunbaetour.domain.admin.certification.dto.response.ShopCertificationDetailResponse;
import com.chunbaetour.domain.admin.certification.dto.response.ShopCertificationListResponse;
import com.chunbaetour.domain.admin.certification.service.AdminShopCertificationService;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.shop.type.ShopCertificationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 상인 인증 관리 API (KAN-204, Admin Epic KAN-177 S05).
 *
 * <p>{@code /api/v1/admin/**} 경로는 SecurityConfig에서 ADMIN 권한 필수.
 *
 * <p>2차 문서의 POST → PATCH 정정 적용 (승인/거절/취소는 인증 신청 status 부분 수정이라 PATCH가 의미상 정확 +
 * 기존 admin/refunds approve PATCH 패턴 일관). 승인/거절/취소 endpoint에 {@link LogAdminAction}을 부착해 S01
 * audit 인프라가 자동 기록한다 — 승인/거절은 {@code targetIdVar = "applicationId"}, 취소는
 * {@code targetIdVar = "shopId"}로 path 변수에서 결정적으로 대상 id를 추출(S01 리뷰 합의). 조회(GET) endpoint는
 * 상태 전이가 없어 audit 미부착.
 */
@Tag(name = "상인 인증 관리 (ADMIN)",
        description = "운영자 인증 신청 목록·상세·승인·거절·취소 (/api/v1/admin/shop-certifications/**)")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminShopCertificationController {

    private final AdminShopCertificationService certificationService;

    @Operation(summary = "인증 신청 목록 조회 (status 필터 + cursor 페이징)")
    @GetMapping("/shop-certifications")
    public ApiResponse<CursorPageResponse<ShopCertificationListResponse>> getCertifications(
            @RequestParam(required = false) ShopCertificationStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(certificationService.getCertifications(status, cursor, size));
    }

    @Operation(summary = "인증 신청 단건 상세 조회")
    @GetMapping("/shop-certifications/{applicationId}")
    public ApiResponse<ShopCertificationDetailResponse> getCertification(
            @PathVariable @Positive Long applicationId
    ) {
        return ApiResponse.success(certificationService.getCertification(applicationId));
    }

    @Operation(summary = "인증 승인 → 가게 인증 마크 자동 부여 (cascade)")
    @PatchMapping("/shop-certifications/{applicationId}/approve")
    @LogAdminAction(actionType = AdminActionType.CERTIFICATION_APPROVE,
            targetType = AdminTargetType.SHOP_CERTIFICATION,
            targetIdVar = "applicationId")
    public ApiResponse<ShopCertificationDetailResponse> approve(
            @PathVariable @Positive Long applicationId,
            @AuthenticationPrincipal Long adminUserId
    ) {
        return ApiResponse.success(certificationService.approve(applicationId, adminUserId));
    }

    @Operation(summary = "인증 거절 (사유 필수)")
    @PatchMapping("/shop-certifications/{applicationId}/reject")
    @LogAdminAction(actionType = AdminActionType.CERTIFICATION_REJECT,
            targetType = AdminTargetType.SHOP_CERTIFICATION,
            targetIdVar = "applicationId")
    public ApiResponse<ShopCertificationDetailResponse> reject(
            @PathVariable @Positive Long applicationId,
            @AuthenticationPrincipal Long adminUserId,
            @Valid @RequestBody CertificationRejectRequest request
    ) {
        return ApiResponse.success(certificationService.reject(applicationId, adminUserId, request.reason()));
    }

    @Operation(summary = "인증 취소 (사유 필수) → 가게 인증 마크 회수")
    @PatchMapping("/shops/{shopId}/certification/cancel")
    @LogAdminAction(actionType = AdminActionType.CERTIFICATION_CANCEL,
            targetType = AdminTargetType.SHOP_CERTIFICATION,
            targetIdVar = "shopId")
    public ApiResponse<Void> cancel(
            @PathVariable @Positive Long shopId,
            @Valid @RequestBody CertificationCancelRequest request
    ) {
        certificationService.cancelCertification(shopId, request.reason());
        return ApiResponse.success();
    }
}
