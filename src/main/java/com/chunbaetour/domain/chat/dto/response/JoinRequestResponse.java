package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.type.JoinRequestStatus;
import com.chunbaetour.domain.community.common.WriterInfo;
import java.time.LocalDateTime;

public record JoinRequestResponse(
        Long joinRequestId,
        WriterInfo applicant,
        String message,
        JoinRequestStatus status,
        LocalDateTime createdAt
) {
    // account null → 탈퇴 계정 처리는 WriterInfo.from()이 담당
    public static JoinRequestResponse from(JoinRequest request, Account account) {
        return new JoinRequestResponse(
                request.getId(),
                WriterInfo.from(account),
                request.getMessage(),
                request.getStatus(),
                request.getCreatedAt()
        );
    }
}
