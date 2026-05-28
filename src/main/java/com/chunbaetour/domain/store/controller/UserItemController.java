package com.chunbaetour.domain.store.controller;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.store.dto.response.UserItemResponse;
import com.chunbaetour.domain.store.service.UserItemService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 보유 아이템 API */
@RestController
@RequestMapping("/api/v1/users/me/items")
@RequiredArgsConstructor
@Validated
public class UserItemController {

    private final UserItemService userItemService;

    /** 내 보유 아이템 조회 — cursor 페이징 */
    @GetMapping
    public ApiResponse<CursorPageResponse<UserItemResponse>> getMyItems(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        requireAuthenticated(userId);
        return ApiResponse.success(userItemService.getMyItems(userId, cursor, size));
    }

    private static void requireAuthenticated(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }
}
