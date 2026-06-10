package com.chunbaetour.domain.festival.dto.response;

import java.util.List;

/**
 * 축제 캐시 목록 래퍼 (KAN-264).
 *
 * <p>Redis 캐시 value를 {@code List<FestivalCacheData>}(루트 JSON 배열)로 저장하면, 타입정보({@code @class})를
 * 배열 루트에 부착할 수 없어 캐시 HIT 시 {@code List<LinkedHashMap>}으로 역직렬화돼 {@code ClassCastException}이
 * 발생한다. 캐시 value의 루트를 '객체'로 만들기 위해 리스트를 이 record로 감싼다 — 루트 객체는 {@code @class}를
 * 가질 수 있어 원소까지 구체 타입으로 복원된다.
 */
public record FestivalCacheList(List<FestivalCacheData> festivals) {
    public static FestivalCacheList of(List<FestivalCacheData> festivals) {
        return new FestivalCacheList(festivals);
    }
}
