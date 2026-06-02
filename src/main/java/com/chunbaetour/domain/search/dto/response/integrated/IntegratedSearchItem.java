package com.chunbaetour.domain.search.dto.response.integrated;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface IntegratedSearchItem {
    @JsonProperty("targetType")
    String getTargetType();
}
