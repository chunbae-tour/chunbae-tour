package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.request.MenuCreateRequest;
import com.chunbaetour.domain.shop.dto.request.MenuUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.MenuResponse;
import com.chunbaetour.domain.shop.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메뉴 CRUD API (STORY-11, 목록 조회 KAN-267).
 * GET    /api/v1/merchants/me/shops/{shopId}/menus          — 내 가게 메뉴 목록 (MERCHANT)
 * POST   /api/v1/merchants/me/shops/{shopId}/menus          — 메뉴 등록 (MERCHANT)
 * PATCH  /api/v1/merchants/me/shops/{shopId}/menus/{menuId} — 메뉴 수정 (MERCHANT)
 * DELETE /api/v1/merchants/me/shops/{shopId}/menus/{menuId} — 메뉴 삭제 (MERCHANT)
 * SecurityConfig: /api/v1/merchants/** → MERCHANT 권한 필요.
 */
@Tag(name = "메뉴 (MERCHANT)", description = "메뉴 목록·등록·수정·삭제 (/api/v1/merchants/me/shops/{shopId}/menus/**)")
@RestController
@RequestMapping("/api/v1/merchants/me/shops/{shopId}/menus")
@RequiredArgsConstructor
@Validated
public class MenuController {

    private final MenuService menuService;

    @Operation(summary = "내 가게 메뉴 목록 조회", description = "본인 가게 메뉴 목록 (KAN-267). 비활성 메뉴 포함, 삭제 메뉴 제외.")
    @GetMapping
    public ApiResponse<List<MenuResponse>> getMenus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId
    ) {
        return ApiResponse.success(menuService.getMenus(userId, shopId));
    }

    @Operation(summary = "메뉴 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MenuResponse> createMenu(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId,
            @Valid @RequestBody MenuCreateRequest request
    ) {
        return ApiResponse.success(menuService.createMenu(userId, shopId, request));
    }

    @Operation(summary = "메뉴 수정")
    @PatchMapping("/{menuId}")
    public ApiResponse<MenuResponse> updateMenu(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId,
            @PathVariable Long menuId,
            @Valid @RequestBody MenuUpdateRequest request
    ) {
        return ApiResponse.success(menuService.updateMenu(userId, shopId, menuId, request));
    }

    @Operation(summary = "메뉴 삭제")
    @DeleteMapping("/{menuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMenu(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId,
            @PathVariable Long menuId
    ) {
        menuService.deleteMenu(userId, shopId, menuId);
    }
}
