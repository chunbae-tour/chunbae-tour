package com.chunbaetour.domain.place.dto.response;

import java.util.List;

public record NearbyPlacePageResponse(
    List<NearbyPlaceResponse> places,
    boolean hasNext
) {}
