package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.Menu;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    Optional<Menu> findByIdAndShopId(Long id, Long shopId);

    boolean existsByShopIdAndName(Long shopId, String name);

    // @SQLRestriction 우회 — 결제 내역 조회 시 soft-deleted 메뉴 정보 표시용
    @Query(nativeQuery = true, value = "SELECT * FROM menus WHERE id = :id")
    Optional<Menu> findByIdIncludingDeleted(@Param("id") Long id);
}
