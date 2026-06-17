package com.chunbaetour.domain.community.free.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 자유게시판 이미지 저장소 추상화 (KAN-317, shop E10 패턴 참고 — 코드 의존 X, 커뮤니티 소유).
 * 구현체: {@code S3PostImageStorage}(@Profile("prod"), S3 PutObject) / {@code LocalDiskPostImageStorage}(그 외, 로컬 디스크).
 * 프로파일당 정확히 하나만 활성화된다.
 */
public interface PostImageStorage {

    /**
     * 검증 완료된 이미지 파일을 저장하고 <b>객체 키</b>를 반환한다(예: {@code posts/free/7/uuid.jpg}).
     *
     * <p>접근 URL이 아니라 키를 반환한다 — 버킷이 비공개라 조회는 presigned GET으로 처리하며,
     * 만료되는 presigned URL을 DB(free_post_images)에 저장하지 않기 위해 키만 영속화한다.
     *
     * @param userId 작성자 ID (저장 경로 구성에 사용 — 업로드 시점엔 postId가 없음)
     * @param file   업로드할 이미지 파일 (PostImageService가 크기·content-type·magic-byte 검증 완료)
     * @return 저장된 객체 키
     */
    String upload(Long userId, MultipartFile file);

    /**
     * 저장된 객체 키로 조회용 presigned GET URL을 발급한다.
     * 비공개 버킷이라 조회는 만료 있는 presigned URL로만 가능하다.
     *
     * @param key 객체 키(예: {@code posts/free/7/uuid.jpg})
     * @return 만료 있는 presigned GET URL (구현체 정책: S3=실제 presign, 로컬=passthrough)
     */
    String presignedGetUrl(String key);
}
