package com.chunbaetour.domain.search.type;

import java.util.Arrays;

public enum SearchSource {
    COMPANION_PLACE_SELECTOR("companion-place-selector", false),
    COMMUNITY_PLACE_SELECTOR("community-place-selector", false),
    DEFAULT("default", true);

    private final String sourceValue;
    private final boolean trackable;

    SearchSource(String sourceValue, boolean trackable) {
        this.sourceValue = sourceValue;
        this.trackable = trackable;
    }

    public boolean isTrackable() {
        return trackable;
    }

    public static SearchSource from(String source) {
        if (source == null || source.isBlank()) {
            return DEFAULT;
        }
        return Arrays.stream(values())
                .filter(s -> s.sourceValue.equalsIgnoreCase(source.trim()))
                .findFirst()
                .orElse(DEFAULT); // 알 수 없는 source가 들어와도 에러를 내기보다는 기본값 처리 (또는 에러 처리 가능)
    }
}
