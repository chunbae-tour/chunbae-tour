package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.ShopImage;
import com.chunbaetour.domain.shop.type.ShopImageType;

/**
 * 가게 사진 단건 응답 (KAN-315, ADR-003).
 *
 * <p>{@code url}은 저장된 객체 키를 presigned GET URL로 변환한 값이다(만료 있음). 운영(S3)은 실제 presign,
 * 로컬은 키 passthrough — {@code ShopImageStorage.presignedGetUrl} 정책을 그대로 따른다.
 *
 * @param imageId   사진 행 ID (삭제 시 경로 변수로 사용)
 * @param type      용도(PROFILE/GALLERY)
 * @param url       presigned GET URL (또는 로컬 passthrough 키)
 * @param sortOrder 갤러리 정렬 순서
 * @param isPrimary 대표 사진 여부
 */
public record ShopImageItemResponse(
        Long imageId,
        ShopImageType type,
        String url,
        int sortOrder,
        boolean isPrimary
) {
    /** 엔티티 + presign 변환된 URL로 응답 생성. presign은 서비스가 수행해 주입한다. */
    public static ShopImageItemResponse of(ShopImage image, String url) {
        return new ShopImageItemResponse(
                image.getId(), image.getType(), url, image.getSortOrder(), image.isPrimary());
    }
}
