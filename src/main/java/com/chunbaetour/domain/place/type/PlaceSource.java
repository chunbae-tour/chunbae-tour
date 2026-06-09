package com.chunbaetour.domain.place.type;

/**
 * 관광지 데이터 출처.
 * MANUAL    — 관리자가 직접 등록한 관광지.
 * API_FETCH — 한국관광공사 KorService2(국문 관광정보)에서 배치 수집한 관광지(KAN-221).
 */
public enum PlaceSource {
    MANUAL, API_FETCH
}
