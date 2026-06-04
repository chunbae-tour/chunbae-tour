package com.chunbaetour.domain.search.dto.request;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.Builder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;

@Builder
public record IntegratedSearchCursor(
        double relevanceScore,
        int priority,
        String targetType,
        Long id
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String encode() {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(this);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public static IntegratedSearchCursor decode(String base64Cursor) {
        if (base64Cursor == null || base64Cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Cursor);
            return OBJECT_MAPPER.readValue(decodedBytes, IntegratedSearchCursor.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
