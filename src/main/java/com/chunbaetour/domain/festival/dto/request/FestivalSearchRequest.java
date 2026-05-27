package com.chunbaetour.domain.festival.dto.request;

import java.time.LocalDate;

/**
 * 사용자 축제 목록 조회 조건 (KAN-97).
 * 컨트롤러에서 @RequestParam으로 직접 바인딩 — record는 참조용.
 */
public record FestivalSearchRequest(
        LocalDate date,
        String region,
        String cursor,
        int size
) {}
