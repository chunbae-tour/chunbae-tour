package com.chunbaetour.domain.community.free.dto;

/**
 * 자유게시판 이미지 업로드 응답 (KAN-317).
 *
 * @param objectKey 업로드된 이미지의 <b>S3 객체 키</b>(예: {@code posts/free/7/uuid.jpg}).
 *                  버킷이 완전 비공개라 클라이언트는 이 키를 글 작성/수정 요청의 {@code imageUrls}에 담아 저장한다.
 * @param previewUrl 작성 화면 미리보기용 presigned GET URL(만료 있음). 비공개 버킷이라 키만으론 못 보므로 즉시 렌더용으로 함께 제공.
 */
public record PostImageUploadResponse(String objectKey, String previewUrl) {
}
