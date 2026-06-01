package com.chunbaetour.domain.shop.dto.response;

/**
 * 가게 이미지 업로드 응답.
 *
 * @param imageUrl 업로드된 이미지의 접근 URL.
 *                 S3 통합 후 public URL 또는 presigned URL 중 어느 방식을 사용할지
 *                 KAN-188 S3 설정 시 결정 필요.
 *                 현재 stub 상태이므로 실제 URL이 반환되지 않음.
 */
public record ShopImageResponse(String imageUrl) {
}
