package com.chunbaetour.domain.cs.dto.response;

import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import java.time.LocalDateTime;

public record SupportRoomResponse(
        Long supportRoomId,
        Long userId,
        Long adminId,
        SupportRoomStatus status,
        LocalDateTime createdAt
) {
    public static SupportRoomResponse from(SupportRoom room) {
        return new SupportRoomResponse(
                room.getId(),
                room.getUserId(),
                room.getAdminId(),
                room.getStatus(),
                room.getCreatedAt()
        );
    }
}
