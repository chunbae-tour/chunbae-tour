package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.profileimage.ProfileImageDisplaySupport;
import com.chunbaetour.domain.chat.entity.Message;
import com.chunbaetour.domain.chat.type.MessageType;
import java.time.LocalDateTime;

/**
 * 채팅 메시지 응답 DTO — STOMP 브로드캐스트(KAN-116) + REST 내역 조회(KAN-120) 공유.
 *
 * <p>현재 동일 필드 구조라 단일 DTO 통합. 향후 응답 필드 분기 시 분리 검토.
 *
 * @param fileUrl IMAGE/FILE만 값 존재. Message.fileUrl(S3 객체 키)을 presigned GET URL로 변환한 값(KAN-309) —
 *                만료 있는 URL이므로 DB에는 키만 저장하고, 응답 시점마다 변환한다.
 */
public record ChatMessageResponse(
        Long messageId,
        Long chatRoomId,
        Long senderId,
        String senderNickname,
        String senderProfileImageUrl,
        MessageType messageType,
        String content,
        String fileUrl,
        String fileName,
        Long fileSize,
        LocalDateTime sentAt
) {
    // sender null: senderId != null → 탈퇴 계정 fallback, senderId == null → SYSTEM 메시지
    // resolvedFileUrl: IMAGE/FILE일 때 호출자가 ChatFileStorage.presignedGetUrl()로 변환해 전달(KAN-309)
    public static ChatMessageResponse from(Message message, Account sender, String resolvedFileUrl) {
        String nickname = sender != null ? sender.getNickname()
                : (message.getSenderId() != null ? "탈퇴한 사용자" : null);
        String profileImageUrl = sender != null
                ? ProfileImageDisplaySupport.toDisplayUrl(sender.getProfileImageUrl()) : null;
        return new ChatMessageResponse(
                message.getId(),
                message.getChatRoomId(),
                message.getSenderId(),
                nickname,
                profileImageUrl,
                message.getMessageType(),
                message.getContent(),
                resolvedFileUrl,
                message.getFileName(),
                message.getFileSize(),
                message.getCreatedAt()
        );
    }
}
