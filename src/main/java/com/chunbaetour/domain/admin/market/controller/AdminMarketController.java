package com.chunbaetour.domain.admin.market.controller;

import com.chunbaetour.domain.admin.audit.AdminActionType;
import com.chunbaetour.domain.admin.audit.AdminTargetType;
import com.chunbaetour.domain.admin.audit.LogAdminAction;
import com.chunbaetour.domain.admin.market.dto.SyncResponse;
import com.chunbaetour.domain.admin.market.dto.response.AdminMarketDetailResponse;
import com.chunbaetour.domain.admin.market.dto.response.AdminMarketListResponse;
import com.chunbaetour.domain.admin.market.service.AdminMarketService;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.market.service.MarketSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전통시장 관리 API.
 * Base URL: {@code /api/v1/admin/traditional-markets}. {@code /api/v1/admin/**}는 SecurityConfig에서 ADMIN 권한 필수.
 *
 * <p>조회(목록/상세)는 공공데이터 sync가 원천이라 읽기 전용(KAN-308). 수정/숨김/삭제는 원천관리 정책(B13) 확정 후 별도.
 */
@Tag(name = "관리", description = "전통시장 데이터 관리 (/api/v1/admin/traditional-markets/**)")
@RestController
@RequestMapping("/api/v1/admin/traditional-markets")
@RequiredArgsConstructor
@Validated
public class AdminMarketController {

    private final MarketSyncService marketSyncService;
    private final AdminMarketService adminMarketService;

    @Operation(summary = "전통시장 목록 조회 (keyword/sido 필터 + cursor 페이징)",
            description = "keyword=시장명 부분일치, sido 미지정 시 전체. 공공데이터 원천이라 조회 전용.")
    @GetMapping
    public ApiResponse<CursorPageResponse<AdminMarketListResponse>> getMarkets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(adminMarketService.getMarkets(keyword, sido, cursor, size));
    }

    @Operation(summary = "전통시장 단건 상세 조회")
    @GetMapping("/{marketId}")
    public ApiResponse<AdminMarketDetailResponse> getMarket(@PathVariable @Positive Long marketId) {
        return ApiResponse.success(adminMarketService.getMarket(marketId));
    }

    @Operation(summary = "전통시장 데이터 즉시 동기화 (관리자 수동 호출)")
    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.OK)
    @LogAdminAction(actionType = AdminActionType.MARKET_SYNC, targetType = AdminTargetType.MARKET, fixedTargetId = 0L)
    public ApiResponse<SyncResponse> syncNow() {
        return ApiResponse.success(marketSyncService.syncAllMarkets());
    }
}
