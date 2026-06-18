package com.chunbaetour.domain.community.free.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FreePostUpdateRequest(
        @Size(min = 1, max = 200) String title,
        @Size(min = 1, max = 5000) String content,
        @Size(max = 5) List<@NotBlank @Size(max = 500) String> imageUrls
) {
}
