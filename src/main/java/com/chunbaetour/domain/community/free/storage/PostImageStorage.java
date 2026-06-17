package com.chunbaetour.domain.community.free.storage;

import java.util.List;
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

    /**
     * 저장된 객체를 삭제한다(KAN-317). 글 수정 시 교체/제거된 옛 이미지, 글 삭제 시 이미지를 즉시 정리한다.
     *
     * <p>best-effort 계약 — 삭제 실패(외부 장애·이미 없음)는 호출자 흐름을 막지 않도록 구현체가 삼키고 로깅한다.
     * 잔존 고아는 reconcile 스케줄러가 회수한다.
     *
     * @param key 객체 키(예: {@code posts/free/7/uuid.jpg})
     */
    void delete(String key);

    /**
     * 주어진 prefix 아래의 모든 객체를 나열한다(KAN-317 고아 cleanup 스케줄러용).
     *
     * <p>고아 reconcile은 저장소 객체 ↔ DB(free_post_images) 참조를 대조해 미참조 객체를 삭제하므로 저장소 전체 목록이 필요하다.
     * S3는 {@code ListObjectsV2} 페이지네이션, 로컬은 디렉터리 walk로 구현한다. 각 항목은 키 + 마지막 수정 시각(grace 판정용)을 담는다.
     *
     * @param prefix 객체 키 prefix(예: {@code posts/free/}) — 정리 대상을 자유게시판 이미지로 한정
     * @return prefix에 속한 객체 메타 목록(없으면 빈 목록)
     */
    List<PostImageObjectInfo> listObjects(String prefix);
}
