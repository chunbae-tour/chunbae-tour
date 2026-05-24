package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.Menu;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    Optional<Menu> findByIdAndShopId(Long id, Long shopId);
}
