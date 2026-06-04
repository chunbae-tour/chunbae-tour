package com.chunbaetour.domain.market.repository;

import com.chunbaetour.domain.market.entity.TraditionalMarket;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TraditionalMarketRepository extends JpaRepository<TraditionalMarket, Long> {

    /** 시장명으로 조회 */
    Optional<TraditionalMarket> findByName(String name);
}
