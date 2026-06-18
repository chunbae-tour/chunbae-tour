package com.chunbaetour.domain.auth.profileimage;

/**
 * 프로필 이미지 업로드 응답 (KAN-320).
 *
 * <p>raw 객체 키는 응답에 싣지 않는다(E10 "raw 키 노출 금지" 계약). 프로필은 1단계 업로드라 키가 이미 본인
 * Account에 반영돼 클라가 키를 따로 저장/회신할 필요가 없다 — 렌더용 presigned URL만 내려준다.
 *
 * @param previewUrl 즉시 렌더용 presigned GET URL(만료 있음). presign 실패 시 null(부분 실패 강등) — 키는 이미 반영됨.
 */
public record ProfileImageUploadResponse(String previewUrl) {
}
