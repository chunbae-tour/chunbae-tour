package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.ShopImage;
import com.chunbaetour.domain.shop.type.ShopImageType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopImageRepository extends JpaRepository<ShopImage, Long> {

    /**
     * 가게 사진 전체 목록 — 대표(PROFILE)가 먼저, 그 다음 갤러리를 sort_order→id 순으로.
     * type은 @Enumerated(STRING)이라 컬럼이 VARCHAR → DESC면 "PROFILE" > "GALLERY"로 PROFILE이 앞선다.
     */
    List<ShopImage> findByShopIdOrderByTypeDescSortOrderAscIdAsc(Long shopId);

    /** 특정 용도 사진 목록 — PROFILE 교체(기존 행 제거)·GALLERY 정렬 계산에 사용. */
    List<ShopImage> findByShopIdAndType(Long shopId, ShopImageType type);

    /** 소유권 검증용 — imageId + shopId 조합 조회. 타 가게 이미지면 빈 Optional(IDOR 차단). */
    Optional<ShopImage> findByIdAndShopId(Long id, Long shopId);
}
