package com.chunbaetour.domain.admin.place.controller;

import com.chunbaetour.domain.admin.audit.AdminActionType;
import com.chunbaetour.domain.admin.audit.AdminTargetType;
import com.chunbaetour.domain.admin.audit.LogAdminAction;
import com.chunbaetour.domain.admin.place.dto.request.AdminPlaceCreateRequest;
import com.chunbaetour.domain.admin.place.dto.request.AdminPlaceUpdateRequest;
import com.chunbaetour.domain.admin.place.dto.response.AdminPlaceDetailResponse;
import com.chunbaetour.domain.admin.place.dto.response.AdminPlaceListResponse;
import com.chunbaetour.domain.admin.place.service.AdminPlaceService;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.place.type.PlaceCategory;
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
 * 운영자 관광지/전통시장 관리 API (Admin Epic KAN-177 S07).
 *
 * <p>{@code /api/v1/admin/**} 경로는 SecurityConfig에서 ADMIN 권한 필수 — 클래스 {@code @PreAuthorize}로 명시
 * (S02/S04/S05 컨벤션 유지).
 *
 * <p>CUD endpoint에 {@link LogAdminAction}을 부착해 S01 audit 인프라가 자동 기록한다. PATCH/DELETE는
 * {@code targetIdVar = "placeId"}로 path 변수에서 결정적으로 대상 id를 추출한다. POST(등록)는 생성 전 id가
 * 없어 path 변수에서 추출 불가 — {@code returnIdField = "id"}로 응답 본문(생성된 관광지 id)에서 targetId를
 * 추출한다(S01 aspect 확장, S07/S08/S09 CREATE 공통). 목록(GET) endpoint는 상태 전이가 없어 audit 미부착.
 */
@Tag(name = "관광지 관리 (ADMIN)", description = "운영자 관광지/전통시장 목록·등록·수정·삭제 (/api/v1/admin/places/**)")
@RestController
@RequestMapping("/api/v1/admin/places")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlaceController {

    private final AdminPlaceService adminPlaceService;

    @Operation(summary = "관광지 목록 조회 (keyword/category 필터 + cursor 페이징)")
    @GetMapping
    public ApiResponse<CursorPageResponse<AdminPlaceListResponse>> getPlaces(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PlaceCategory category,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(adminPlaceService.getPlaces(keyword, category, cursor, size));
    }

    @Operation(summary = "관광지 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @LogAdminAction(actionType = AdminActionType.PLACE_CREATE,
            targetType = AdminTargetType.PLACE,
            returnIdField = "id")
    public ApiResponse<AdminPlaceDetailResponse> createPlace(
            @Valid @RequestBody AdminPlaceCreateRequest request
    ) {
        return ApiResponse.success(adminPlaceService.createPlace(request));
    }

    @Operation(summary = "관광지 수정 (partial update)")
    @PatchMapping("/{placeId}")
    @LogAdminAction(actionType = AdminActionType.PLACE_UPDATE,
            targetType = AdminTargetType.PLACE,
            targetIdVar = "placeId")
    public ApiResponse<AdminPlaceDetailResponse> updatePlace(
            @PathVariable @Positive Long placeId,
            @Valid @RequestBody AdminPlaceUpdateRequest request
    ) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return ApiResponse.success(adminPlaceService.updatePlace(placeId, request));
    }

    @Operation(summary = "관광지 삭제 (soft delete)")
    @DeleteMapping("/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @LogAdminAction(actionType = AdminActionType.PLACE_DELETE,
            targetType = AdminTargetType.PLACE,
            targetIdVar = "placeId")
    public void deletePlace(@PathVariable @Positive Long placeId) {
        adminPlaceService.deletePlace(placeId);
    }
}
