package com.chunbaetour.domain.cs.dto.response;

import com.chunbaetour.domain.cs.entity.Faq;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record FaqResponse(
        Long faqId,
        String question,
        String answer,
        String category,
        @JsonProperty("isActive") boolean isActive,
        LocalDateTime createdAt
) {
    public static FaqResponse from(Faq faq) {
        return new FaqResponse(
                faq.getId(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.getCategory(),
                faq.isActive(),
                faq.getCreatedAt()
        );
    }
}
