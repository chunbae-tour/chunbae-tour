package com.chunbaetour.domain.cs.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 상담 채팅 파일/이미지 저장소 추상화 (KAN-310, E10 패턴 재사용).
 * 구현체: {@code S3SupportFileStorage}(@Profile("prod"), S3 PutObject) / {@code LocalDiskSupportFileStorage}(그 외, 로컬 디스크).
 * 프로파일당 정확히 하나만 활성화된다.
 */
public interface SupportFileStorage {

    /**
     * 검증 완료된 파일을 저장하고 <b>객체 키</b>를 반환한다(예: {@code support-rooms/10/uuid.jpg}).
     *
     * <p>접근 URL이 아니라 키를 반환한다 — 버킷이 비공개라 조회는 presigned GET으로 처리하며,
     * 만료되는 presigned URL을 SupportMessage.fileUrl에 저장하지 않기 위해 키만 영속화한다.
     *
     * @param supportRoomId 상담방 ID (저장 경로 구성에 사용)
     * @param file          업로드할 파일 (SupportFileService가 크기·content-type·magic-byte 검증 완료)
     * @return 저장된 객체 키
     */
    String upload(Long supportRoomId, MultipartFile file);

    /**
     * 저장된 객체 키로 조회용 presigned GET URL을 발급한다.
     * 비공개 버킷이라 조회는 만료 있는 presigned URL로만 가능하다.
     *
     * @param key 객체 키(예: {@code support-rooms/10/uuid.jpg})
     * @return 만료 있는 presigned GET URL (구현체 정책: S3=실제 presign, 로컬=passthrough)
     */
    String presignedGetUrl(String key);
}
