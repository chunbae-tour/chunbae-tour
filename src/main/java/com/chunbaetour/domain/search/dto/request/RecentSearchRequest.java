package com.chunbaetour.domain.search.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 최근 검색어 저장 요청 DTO
 */
public record RecentSearchRequest(
        @NotBlank(message = "검색어는 필수입니다.")
        @Size(max = 50, message = "검색어는 최대 50자까지 입력 가능합니다.")
        String keyword
) {}
