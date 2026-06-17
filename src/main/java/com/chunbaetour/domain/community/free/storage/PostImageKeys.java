package com.chunbaetour.domain.community.free.storage;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.UUID;

/**
 * 자유게시판 이미지 S3 객체 키 생성 규칙 (KAN-317).
 *
 * <p>키 형식: {@code posts/free/{userId}/{uuid}.{ext}}.
 * <ul>
 *   <li>업로드 시점엔 글(postId)이 아직 없으므로(작성 전 첨부) prefix를 <b>작성자 userId</b> 기준으로 둔다.</li>
 *   <li>파일명은 클라이언트 originalFilename을 절대 쓰지 않고 {@link UUID}로 재생성한다(path traversal 차단).</li>
 *   <li>확장자는 <b>검증 통과한</b> content-type에서 파생한다(PostImageService가 declared+magic-byte 검증 후 위임).</li>
 * </ul>
 *
 * <p>shop({@code ShopImageKeys})·chat({@code ChatFileKeys}) 패턴을 참고했으나 커뮤니티 도메인이 소유한다(코드 의존 X).
 */
public final class PostImageKeys {

    private PostImageKeys() {
    }

    /** content-type → 객체 키. 허용 타입(jpeg/png/webp) 외에는 호출되지 않는다(서비스가 선검증). */
    public static String objectKey(Long userId, String contentType) {
        return prefix(userId) + UUID.randomUUID() + "." + extensionFor(contentType);
    }

    /** 작성자별 키 prefix({@code posts/free/{userId}/}). */
    public static String prefix(Long userId) {
        return "posts/free/" + userId + "/";
    }

    /**
     * 키가 해당 작성자 소유 prefix({@code posts/free/{userId}/})에 속하는지.
     * 글 작성/수정 시 imageUrls(객체 키) 검증·presign 발급에 사용 — 타인/임의 객체 키 차단(IDOR 방지).
     */
    public static boolean belongsToUser(String key, Long userId) {
        // userId null이면 prefix("posts/free/null/")로 오인 매칭될 수 있어 명시 차단(불변식 고정).
        return key != null && userId != null && key.startsWith(prefix(userId));
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) {
            throw new BusinessException(ErrorCode.POST_IMAGE_TYPE_UNSUPPORTED);
        }
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new BusinessException(ErrorCode.POST_IMAGE_TYPE_UNSUPPORTED);
        };
    }
}
