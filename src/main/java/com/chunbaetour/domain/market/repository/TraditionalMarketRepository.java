package com.chunbaetour.domain.market.repository;

import com.chunbaetour.domain.market.entity.TraditionalMarket;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TraditionalMarketRepository extends JpaRepository<TraditionalMarket, Long> {

    /** 시장명으로 조회 */
    Optional<TraditionalMarket> findByName(String name);

    /** 시장명 + 주소로 조회 (동명 이장 대응, upsert 키) */
    Optional<TraditionalMarket> findByNameAndAddress(String name, String address);
}
