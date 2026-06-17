package com.chunbaetour.domain.community.free.storage;

import java.time.Instant;

/**
 * 저장소 객체 메타데이터 (KAN-317 고아 cleanup).
 *
 * <p>고아 reconcile 스케줄러가 저장소를 나열할 때 쓰는 최소 정보 — 객체 키와 마지막 수정 시각.
 * {@code lastModified}는 grace period 판정에 쓴다(작성 중인 임시 업로드를 청소가 오삭제하는 것 방지).
 *
 * @param objectKey    객체 키(예: {@code posts/free/7/uuid.jpg})
 * @param lastModified 객체 마지막 수정 시각(UTC Instant — S3 LastModified / 로컬 파일 mtime)
 */
public record PostImageObjectInfo(String objectKey, Instant lastModified) {
}
