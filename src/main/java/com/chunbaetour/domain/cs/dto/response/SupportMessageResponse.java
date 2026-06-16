package com.chunbaetour.domain.cs.dto.response;

import com.chunbaetour.domain.cs.entity.SupportMessage;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import java.time.LocalDateTime;

/**
 * @param fileUrl IMAGE/FILE만 값 존재. SupportMessage.fileUrl(S3 객체 키)을 presigned GET URL로 변환한 값(KAN-310) —
 *                만료 있는 URL이므로 DB에는 키만 저장하고, 응답 시점마다 변환한다.
 */
public record SupportMessageResponse(
        Long messageId,
        Long senderId,
        SupportSenderRole senderRole,
        SupportMessageType messageType,
        String content,
        String fileUrl,
        String fileName,
        Long fileSize,
        LocalDateTime sentAt
) {
    // resolvedFileUrl: IMAGE/FILE일 때 호출자가 SupportFileStorage.presignedGetUrl()로 변환해 전달(KAN-310)
    public static SupportMessageResponse from(SupportMessage message, String resolvedFileUrl) {
        return new SupportMessageResponse(
                message.getId(),
                message.getSenderId(),
                message.getSenderRole(),
                message.getMessageType(),
                message.getContent(),
                resolvedFileUrl,
                message.getFileName(),
                message.getFileSize(),
                message.getSentAt()
        );
    }
}
