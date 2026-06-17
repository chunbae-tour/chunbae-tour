package com.chunbaetour.domain.shop.storage;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 가게 이미지 저장소 추상화 (E10).
 * 구현체: {@code S3ShopImageStorage}(@Profile("prod"), S3 PutObject) / {@code LocalDiskShopImageStorage}(그 외, 로컬 디스크).
 * 프로파일당 정확히 하나만 활성화된다.
 */
public interface ShopImageStorage {

    /**
     * 검증 완료된 이미지 파일을 저장하고 <b>객체 키</b>를 반환한다(예: {@code shops/10/uuid.jpg}).
     *
     * <p>접근 URL이 아니라 키를 반환한다 — 버킷이 비공개라 조회는 presigned GET으로 처리하며(PR3),
     * 만료되는 presigned URL을 DB에 저장하지 않기 위해 키만 영속화한다.
     *
     * @param shopId 가게 ID (저장 경로 구성에 사용)
     * @param file   업로드할 이미지 파일 (ShopImageService가 크기·content-type·magic-byte 검증 완료)
     * @return 저장된 객체 키
     */
    String upload(Long shopId, MultipartFile file);

    /**
     * 저장된 객체 키로 조회용 presigned GET URL을 발급한다(E10 PR3).
     * 비공개 버킷이라 조회는 만료 있는 presigned URL로만 가능하다.
     *
     * @param key 객체 키(예: {@code shops/10/uuid.jpg})
     * @return 만료 있는 presigned GET URL (구현체 정책: S3=실제 presign, 로컬=passthrough)
     */
    String presignedGetUrl(String key);

    /**
     * 저장된 객체를 삭제한다(KAN-315, ADR-003). 가게 사진 행 삭제·대표사진 교체 시 고아 객체를 즉시 제거한다.
     *
     * <p>best-effort 계약 — 삭제 실패(외부 장애·이미 없음)는 호출자 흐름을 막지 않도록 구현체가 삼키고 로깅한다.
     * 행(DB)이 진실 원천이고, 잔존 고아 객체는 후속 cleanup 스케줄러(KAN-298 패턴)가 회수한다.
     *
     * @param key 객체 키(예: {@code shops/10/uuid.jpg})
     */
    void delete(String key);

    /**
     * 주어진 prefix 아래의 모든 객체를 나열한다(KAN-319 고아 cleanup 스케줄러용).
     *
     * <p>고아 reconcile은 저장소 객체 ↔ DB 행을 대조해 DB에 없는 객체를 삭제하므로, 저장소 측 전체 목록이 필요하다.
     * S3는 {@code ListObjectsV2} 페이지네이션, 로컬은 디렉터리 walk로 구현한다. 각 항목은 키 + 마지막 수정 시각을 담는다
     * (수정 시각은 grace period 판정에 사용 — 갓 올라온 객체 오삭제 방지).
     *
     * @param prefix 객체 키 prefix(예: {@code shops/}) — 정리 대상을 가게 이미지로 한정
     * @return prefix에 속한 객체 메타 목록(없으면 빈 목록)
     */
    List<ShopImageObjectInfo> listObjects(String prefix);
}
