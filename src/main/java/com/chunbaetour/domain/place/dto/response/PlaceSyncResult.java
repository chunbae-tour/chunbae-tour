package com.chunbaetour.domain.place.dto.response;

/**
 * 관광지 외부 API 수집 결과 집계 (KAN-221).
 * fetched=API에서 받은 건수, created=신규 저장, updated=기존 갱신, skipped=검증 실패·중복 등 건너뜀.
 */
public record PlaceSyncResult(int fetched, int created, int updated, int skipped) {}
