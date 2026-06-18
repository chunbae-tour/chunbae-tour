package com.chunbaetour.domain.auth.profileimage;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 프로필 이미지 S3 객체 키 생성 규칙 (KAN-320).
 *
 * <p>키 형식: {@code users/{userId}/profile/{uuid}.{ext}}.
 * <ul>
 *   <li>파일명은 클라이언트 originalFilename을 절대 쓰지 않고 {@link UUID}로 재생성한다(path traversal 차단).</li>
 *   <li>확장자는 <b>검증 통과한</b> content-type에서 파생한다(ProfileImageService가 declared+magic-byte 검증 후 위임).</li>
 * </ul>
 *
 * <p>{@code profile_image_url}에는 우리 업로드 키 외에 OAuth 공급자 외부 URL(http...)도 저장될 수 있다 →
 * {@link #isOwnedKey}로 "우리 키"만 식별해 presign/삭제 대상으로 삼고, 외부 URL은 그대로 둔다(이중 출처).
 */
public final class ProfileImageKeys {

    /** 우리 업로드 키 계약: users/{userId}/profile/{uuid}.{jpg|png|webp}. */
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^users/\\d+/profile/[0-9a-fA-F\\-]{36}\\.(jpg|png|webp)$");

    private ProfileImageKeys() {
    }

    /** content-type → 객체 키. 허용 타입(jpeg/png/webp) 외에는 호출되지 않는다(서비스가 선검증). */
    public static String objectKey(Long userId, String contentType) {
        return prefix(userId) + UUID.randomUUID() + "." + extensionFor(contentType);
    }

    /** 사용자별 키 prefix({@code users/{userId}/profile/}). */
    public static String prefix(Long userId) {
        return "users/" + userId + "/profile/";
    }

    /**
     * 저장값이 <b>우리가 업로드한 S3 키</b>인지(외부 OAuth URL과 구분). prefix + 키 패턴을 모두 만족해야 true.
     * presign 변환·옛 객체 삭제 대상 판정에 사용 — 외부 http URL은 false라 passthrough/미삭제.
     */
    public static boolean isOwnedKey(String value, Long userId) {
        if (value == null || userId == null) {
            return false;
        }
        return value.startsWith(prefix(userId)) && KEY_PATTERN.matcher(value).matches();
    }

    /** userId 무관하게 "우리 키 형식"인지 — 조회 변환(어느 유저 것이든 presign) 판정용. */
    public static boolean isOwnedKey(String value) {
        return value != null && KEY_PATTERN.matcher(value).matches();
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_TYPE_UNSUPPORTED);
        }
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new BusinessException(ErrorCode.PROFILE_IMAGE_TYPE_UNSUPPORTED);
        };
    }
}
