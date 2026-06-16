package com.chunbaetour.domain.report.dto.response;

public record PendingCountResponse(long count) {

    public static PendingCountResponse of(long count) {
        return new PendingCountResponse(count);
    }
}
