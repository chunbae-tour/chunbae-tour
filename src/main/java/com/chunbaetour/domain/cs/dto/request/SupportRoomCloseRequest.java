package com.chunbaetour.domain.cs.dto.request;

import jakarta.validation.constraints.Size;

public record SupportRoomCloseRequest(
        // 선택값 — 상담 종료 시 관리자 메모
        @Size(max = 1000) String summary
) {}
