package com.chunbaetour.domain.shop.storage;

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
}
