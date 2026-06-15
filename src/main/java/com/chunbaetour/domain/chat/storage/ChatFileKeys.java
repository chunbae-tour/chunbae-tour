package com.chunbaetour.domain.chat.storage;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.UUID;

/**
 * 채팅 파일/이미지 S3 객체 키 생성 규칙 (KAN-309).
 *
 * <p>키 형식: {@code chat-rooms/{chatRoomId}/{uuid}.{ext}}.
 * <ul>
 *   <li>파일명은 클라이언트 originalFilename을 절대 쓰지 않고 {@link UUID}로 재생성한다(path traversal 차단).</li>
 *   <li>확장자는 <b>검증 통과한</b> content-type에서 파생한다(ChatFileService가 declared+magic-byte로 검증 후 위임).</li>
 * </ul>
 */
public final class ChatFileKeys {

    private ChatFileKeys() {
    }

    /** content-type → 객체 키. 허용 타입 외에는 호출되지 않는다(서비스가 선검증). */
    public static String objectKey(Long chatRoomId, String contentType) {
        return prefix(chatRoomId) + UUID.randomUUID() + "." + extensionFor(contentType);
    }

    /** 채팅방별 키 prefix({@code chat-rooms/{chatRoomId}/}). */
    public static String prefix(Long chatRoomId) {
        return "chat-rooms/" + chatRoomId + "/";
    }

    /**
     * 키가 해당 채팅방 소유 prefix({@code chat-rooms/{chatRoomId}/})에 속하는지.
     * 메시지 전송 시 fileUrl(객체 키) 검증에 사용 — 타 채팅방/임의 객체 키 차단(IDOR 방지).
     */
    public static boolean belongsToChatRoom(String key, Long chatRoomId) {
        // chatRoomId null이면 prefix("chat-rooms/null/")로 오인 매칭될 수 있어 명시 차단(불변식 고정).
        return key != null && chatRoomId != null && key.startsWith(prefix(chatRoomId));
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) {
            throw new BusinessException(ErrorCode.CHAT_FILE_TYPE_UNSUPPORTED);
        }
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
            case "application/x-hwp" -> "hwp";
            default -> throw new BusinessException(ErrorCode.CHAT_FILE_TYPE_UNSUPPORTED);
        };
    }
}
