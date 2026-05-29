package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.ShopWallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopWalletRepository extends JpaRepository<ShopWallet, Long> {

    Optional<ShopWallet> findByShopId(Long shopId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM ShopWallet w WHERE w.shopId = :shopId")
    Optional<ShopWallet> findByShopIdWithLock(@Param("shopId") Long shopId);
}
