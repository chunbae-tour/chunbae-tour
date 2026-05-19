package com.chunbaetour.domain.place.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.place.dto.request.NearbyPlaceRequest;
import com.chunbaetour.domain.place.dto.response.NearbyPlacePageResponse;
import com.chunbaetour.domain.place.service.PlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {
    
    private final PlaceService placeService;

    @GetMapping("/nearby")
    public ApiResponse<NearbyPlacePageResponse> getNearbyPlaces(@Valid @ModelAttribute NearbyPlaceRequest request) {
        NearbyPlacePageResponse response = placeService.findNearby(
                request.lat(),
                request.lng(),
                request.radius(),
                request.cursor(),
                request.cursorDistance(),
                request.size()
        );
        return ApiResponse.success(response);
    }
}

