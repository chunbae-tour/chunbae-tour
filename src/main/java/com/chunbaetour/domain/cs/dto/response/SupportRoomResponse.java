package com.chunbaetour.domain.cs.dto.response;

import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import java.time.LocalDateTime;

public record SupportRoomResponse(
        Long supportRoomId,
        Long userId,
        Long adminId,
        SupportRoomStatus status,
        String summary,
        LocalDateTime closedAt,
        LocalDateTime createdAt
) {
    // ADMIN용 — summary 포함
    public static SupportRoomResponse from(SupportRoom room) {
        return new SupportRoomResponse(
                room.getId(),
                room.getUserId(),
                room.getAdminId(),
                room.getStatus(),
                room.getSummary(),
                room.getClosedAt(),
                room.getCreatedAt()
        );
    }

    // USER용 — summary 제외 (ADMIN 내부 메모 노출 방지)
    public static SupportRoomResponse fromForUser(SupportRoom room) {
        return new SupportRoomResponse(
                room.getId(),
                room.getUserId(),
                room.getAdminId(),
                room.getStatus(),
                null,
                room.getClosedAt(),
                room.getCreatedAt()
        );
    }
}
