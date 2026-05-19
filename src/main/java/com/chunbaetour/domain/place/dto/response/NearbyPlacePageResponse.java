package com.chunbaetour.domain.place.dto.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NearbyPlacePageResponse {

    private List<NearbyPlaceResponse> places;
    private boolean hasNext;

    public NearbyPlacePageResponse(List<NearbyPlaceResponse> places, boolean hasNext) {
        this.places = places;
        this.hasNext = hasNext;
    }
}
