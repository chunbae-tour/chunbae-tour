package com.chunbaetour.domain.cs.storage;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.UUID;

/**
 * 상담 채팅 파일/이미지 S3 객체 키 생성 규칙 (KAN-309 CS).
 *
 * <p>키 형식: {@code support-rooms/{supportRoomId}/{uuid}.{ext}}.
 * <ul>
 *   <li>파일명은 클라이언트 originalFilename을 절대 쓰지 않고 {@link UUID}로 재생성한다(path traversal 차단).</li>
 *   <li>확장자는 <b>검증 통과한</b> content-type에서 파생한다(SupportFileService가 declared+magic-byte로 검증 후 위임).</li>
 * </ul>
 */
public final class SupportFileKeys {

    private SupportFileKeys() {
    }

    /** content-type → 객체 키. 허용 타입 외에는 호출되지 않는다(서비스가 선검증). */
    public static String objectKey(Long supportRoomId, String contentType) {
        return prefix(supportRoomId) + UUID.randomUUID() + "." + extensionFor(contentType);
    }

    /** 상담방별 키 prefix({@code support-rooms/{supportRoomId}/}). */
    public static String prefix(Long supportRoomId) {
        return "support-rooms/" + supportRoomId + "/";
    }

    /**
     * 키가 해당 상담방 소유 prefix({@code support-rooms/{supportRoomId}/})에 속하는지.
     * 메시지 전송 시 fileUrl(객체 키) 검증에 사용 — 타 상담방/임의 객체 키 차단(IDOR 방지).
     */
    public static boolean belongsToSupportRoom(String key, Long supportRoomId) {
        // supportRoomId null이면 prefix("support-rooms/null/")로 오인 매칭될 수 있어 명시 차단(불변식 고정).
        return key != null && supportRoomId != null && key.startsWith(prefix(supportRoomId));
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) {
            throw new BusinessException(ErrorCode.SUPPORT_FILE_TYPE_UNSUPPORTED);
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
            default -> throw new BusinessException(ErrorCode.SUPPORT_FILE_TYPE_UNSUPPORTED);
        };
    }
}
