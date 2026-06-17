package com.chunbaetour.domain.auth.profileimage;

/**
 * 프로필 이미지 업로드 응답 (KAN-320).
 *
 * @param objectKey  업로드된 이미지의 S3 객체 키(예: {@code users/3/profile/uuid.jpg}). 이미 본인 프로필에 반영됨(1단계).
 * @param previewUrl 즉시 렌더용 presigned GET URL(만료 있음). presign 실패 시 null(부분 실패 강등) — 키는 이미 반영됨.
 */
public record ProfileImageUploadResponse(String objectKey, String previewUrl) {
}
