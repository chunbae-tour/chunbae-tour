package com.chunbaetour.domain.shop.dto.response;

/**
 * 가게 이미지 업로드 응답 (E10).
 *
 * @param objectKey 업로드된 이미지의 <b>S3 객체 키</b>(예: {@code shops/10/uuid.jpg}).
 *                  버킷이 완전 비공개라 public URL이 아닌 키를 반환한다 — 클라이언트는 이 키를 기존
 *                  가게 수정 API(imageUrls)에 저장하고, 조회 시 서버가 presigned GET URL로 변환해 내려준다(PR3).
 *                  presigned URL은 만료가 있어 DB에 저장하지 않는다(키만 저장).
 */
public record ShopImageResponse(String objectKey) {
}
