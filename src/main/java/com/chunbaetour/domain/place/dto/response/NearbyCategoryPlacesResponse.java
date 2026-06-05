package com.chunbaetour.domain.place.dto.response;

import java.util.List;

public record NearbyCategoryPlacesResponse(
    List<NearbyCategoryItem> items,
    boolean hasNext
) {
    public record NearbyCategoryItem(
        String id,
        String placeName,
        String categoryName,
        String phone,
        String addressName,
        String roadAddressName,
        double lat,
        double lng,
        String placeUrl,
        int distance
    ) {}
}
