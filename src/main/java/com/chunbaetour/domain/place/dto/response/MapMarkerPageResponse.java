package com.chunbaetour.domain.place.dto.response;

import java.util.List;

public record MapMarkerPageResponse(
        List<MapMarkerResponse> markers,
        boolean truncated,
        int limit
) {
}
