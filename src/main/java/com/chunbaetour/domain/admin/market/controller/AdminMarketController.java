package com.chunbaetour.domain.admin.market.controller;

import com.chunbaetour.domain.admin.audit.AdminActionType;
import com.chunbaetour.domain.admin.audit.AdminTargetType;
import com.chunbaetour.domain.admin.audit.LogAdminAction;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.market.service.MarketSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전통시장 관리 API.
 * Base URL: {@code /api/v1/admin/traditional-markets}
 */
@Tag(name = "관리", description = "전통시장 데이터 관리 (/api/v1/admin/traditional-markets/**)")
@RestController
@RequestMapping("/api/v1/admin/traditional-markets")
@RequiredArgsConstructor
public class AdminMarketController {

    private final MarketSyncService marketSyncService;

    @Operation(summary = "전통시장 데이터 즉시 동기화 (관리자 수동 호출)")
    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @LogAdminAction(actionType = AdminActionType.MARKET_SYNC, targetType = AdminTargetType.MARKET)
    public ApiResponse<SyncResponse> syncNow() {
        int synced = marketSyncService.syncAllMarkets();
        return ApiResponse.success(new SyncResponse(synced, "전통시장 데이터 동기화 완료"));
    }

    record SyncResponse(int synced, String message) {}
}
