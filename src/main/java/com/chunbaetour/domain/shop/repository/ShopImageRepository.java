package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.ShopImage;
import com.chunbaetour.domain.shop.type.ShopImageType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopImageRepository extends JpaRepository<ShopImage, Long> {

    /**
     * 가게 사진 전체 목록 — 대표(PROFILE)가 먼저, 그 다음 갤러리를 sort_order→id 순으로.
     * type은 @Enumerated(STRING)이라 컬럼이 VARCHAR → DESC면 "PROFILE" > "GALLERY"로 PROFILE이 앞선다.
     */
    List<ShopImage> findByShopIdOrderByTypeDescSortOrderAscIdAsc(Long shopId);

    /** 특정 용도 사진 목록 — GALLERY 정렬(sort_order max) 계산에 사용. */
    List<ShopImage> findByShopIdAndType(Long shopId, ShopImageType type);

    /**
     * PROFILE 교체용 잠금 조회 — 기존 PROFILE 행을 SELECT ... FOR UPDATE로 읽는다.
     *
     * <p>왜 잠금 읽기인가: 업로드 흐름이 먼저 비잠금으로 Shop을 읽어(소유권 검증) REPEATABLE READ 스냅샷이
     * 일찍 고정되므로, 이후 일반 SELECT는 동시 트랜잭션이 커밋한 PROFILE 행을 보지 못한다(stale snapshot).
     * 잠금 읽기는 최신 커밋본을 읽어, Shop 행 비관락으로 직렬화된 다음 트랜잭션이 직전에 생성된 PROFILE을
     * 정확히 보고 교체(delete)하도록 한다 → "가게당 PROFILE 1장" 불변식 보장.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ShopImage i WHERE i.shopId = :shopId AND i.type = :type")
    List<ShopImage> findByShopIdAndTypeForUpdate(@Param("shopId") Long shopId, @Param("type") ShopImageType type);

    /** 소유권 검증용 — imageId + shopId 조합 조회. 타 가게 이미지면 빈 Optional(IDOR 차단). */
    Optional<ShopImage> findByIdAndShopId(Long id, Long shopId);
}
