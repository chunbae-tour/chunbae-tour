package com.chunbaetour.domain.shop.storage;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.UUID;

/**
 * 가게 이미지 S3 객체 키 생성 규칙 (E10).
 *
 * <p>키 형식: {@code shops/{shopId}/{uuid}.{ext}}.
 * <ul>
 *   <li>파일명은 클라이언트 originalFilename을 절대 쓰지 않고 {@link UUID}로 재생성한다(path traversal 차단).</li>
 *   <li>확장자는 <b>검증 통과한</b> content-type에서 파생한다(ShopImageService가 declared+magic-byte로 검증 후 위임).</li>
 * </ul>
 */
public final class ShopImageKeys {

    private ShopImageKeys() {
    }

    /** content-type → 객체 키. 허용 타입(jpeg/png/webp) 외에는 호출되지 않는다(서비스가 선검증). */
    public static String objectKey(Long shopId, String contentType) {
        return prefix(shopId) + UUID.randomUUID() + "." + extensionFor(contentType);
    }

    /** 가게별 키 prefix({@code shops/{shopId}/}). */
    public static String prefix(Long shopId) {
        return "shops/" + shopId + "/";
    }

    /**
     * 키가 해당 가게 소유 prefix({@code shops/{shopId}/})에 속하는지.
     * imageUrls 저장 검증·presign 발급 시 타 가게/임의 객체 키 차단(IDOR 방지, hyeonmin02 리뷰).
     */
    public static boolean belongsToShop(String key, Long shopId) {
        // shopId null이면 prefix("shops/null/")로 오인 매칭될 수 있어 명시 차단(불변식 고정, DGAZA-max 리뷰).
        return key != null && shopId != null && key.startsWith(prefix(shopId));
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) {
            throw new BusinessException(ErrorCode.SHOP_IMAGE_TYPE_UNSUPPORTED);
        }
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new BusinessException(ErrorCode.SHOP_IMAGE_TYPE_UNSUPPORTED);
        };
    }
}
