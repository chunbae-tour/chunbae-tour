package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.Shop;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    /** userId로 가게 단건 조회 — 상인 1:1 관계 */
    Optional<Shop> findByUserId(Long userId);

    /** 가게 중복 생성 방지 검사 */
    boolean existsByUserId(Long userId);
}
