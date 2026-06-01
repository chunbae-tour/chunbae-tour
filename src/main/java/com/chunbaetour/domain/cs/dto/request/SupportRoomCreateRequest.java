package com.chunbaetour.domain.cs.dto.request;

import jakarta.validation.constraints.Size;

public record SupportRoomCreateRequest(
        // 선택값 — 제공 시 SupportMessage(TEXT) 함께 생성
        @Size(max = 1000) String initialMessage
) {}
