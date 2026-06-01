package com.chunbaetour.domain.shop.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 가게 이미지 저장소 추상화 — S3 통합 시 이 인터페이스만 구현하면 됨.
 * 현재 구현체: NoOpShopImageStorage (stub, 항상 EXTERNAL_SERVICE_ERROR)
 * 후속 구현체: S3ShopImageStorage (KAN-188 S3 설정 완료 후 bean 교체)
 */
public interface ShopImageStorage {

    /**
     * 이미지 파일을 저장하고 접근 URL을 반환한다.
     *
     * @param shopId 가게 ID (저장 경로 구성에 사용)
     * @param file   업로드할 이미지 파일 (검증 완료 상태)
     * @return 저장된 이미지의 접근 URL (public URL 또는 presigned URL — 구현체 정책에 따름)
     */
    String upload(Long shopId, MultipartFile file);
}
