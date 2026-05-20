package com.chunbaetour.domain.community.free.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record FreePostUpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 5000) String content,
        @Size(max = 10) List<String> imageUrls
) {
}
