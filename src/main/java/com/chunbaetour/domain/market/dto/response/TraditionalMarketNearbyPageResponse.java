package com.chunbaetour.domain.market.dto.response;

import java.util.List;

public record TraditionalMarketNearbyPageResponse(
        List<TraditionalMarketNearbyResponse> markets,
        boolean hasNext
) {}
