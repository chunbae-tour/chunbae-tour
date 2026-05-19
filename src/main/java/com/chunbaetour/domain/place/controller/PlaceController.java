package com.chunbaetour.domain.place.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.place.dto.request.NearbyPlaceRequest;
import com.chunbaetour.domain.place.dto.response.NearbyPlaceResponse;
import com.chunbaetour.domain.place.service.PlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {
    
    private final PlaceService placeService;

    @GetMapping("/nearby")
    public ApiResponse<List<NearbyPlaceResponse>> getNearbyPlaces(@Valid @ModelAttribute NearbyPlaceRequest request) {
        List<NearbyPlaceResponse> response = placeService.findNearby(
                request.getLat(),
                request.getLng(),
                request.getRadius(),
                request.getCursor(),
                request.getSize()
        );
        return ApiResponse.success(response);
    }
}

