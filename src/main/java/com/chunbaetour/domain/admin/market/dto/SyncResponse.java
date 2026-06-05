package com.chunbaetour.domain.admin.market.dto;

/**
 * 전통시장 데이터 동기화 응답 DTO.
 */
public record SyncResponse(
        int insertedCount,
        int updatedCount,
        int skippedCount,
        String message
) {}
