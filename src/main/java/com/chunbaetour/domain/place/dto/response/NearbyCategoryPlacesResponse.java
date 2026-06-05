package com.chunbaetour.domain.place.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NearbyCategoryPlacesResponse {
    private List<NearbyCategoryItem> items;
    private boolean hasNext;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NearbyCategoryItem {
        private String externalId;
        private String placeName;
        private String categoryName;
        private String phone;
        private String addressName;
        private String roadAddressName;
        private double lat;
        private double lng;
        private String placeUrl;
        private int distance;
    }
}
