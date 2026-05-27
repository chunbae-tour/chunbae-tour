package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.request.MenuCreateRequest;
import com.chunbaetour.domain.shop.dto.request.MenuUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.MenuResponse;
import com.chunbaetour.domain.shop.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메뉴 CRUD API (STORY-11).
 * POST   /api/v1/merchants/me/shop/menus         — 메뉴 등록 (MERCHANT)
 * PATCH  /api/v1/merchants/me/shop/menus/{menuId} — 메뉴 수정 (MERCHANT)
 * DELETE /api/v1/merchants/me/shop/menus/{menuId} — 메뉴 삭제 (MERCHANT)
 * SecurityConfig: /api/v1/merchants/** → MERCHANT 권한 필요.
 */
@RestController
@RequestMapping("/api/v1/merchants/me/shop/menus")
@RequiredArgsConstructor
@Validated
public class MenuController {

    private final MenuService menuService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MenuResponse> createMenu(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MenuCreateRequest request
    ) {
        return ApiResponse.success(menuService.createMenu(userId, request));
    }

    @PatchMapping("/{menuId}")
    public ApiResponse<MenuResponse> updateMenu(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long menuId,
            @Valid @RequestBody MenuUpdateRequest request
    ) {
        return ApiResponse.success(menuService.updateMenu(userId, menuId, request));
    }

    @DeleteMapping("/{menuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMenu(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long menuId
    ) {
        menuService.deleteMenu(userId, menuId);
    }
}
