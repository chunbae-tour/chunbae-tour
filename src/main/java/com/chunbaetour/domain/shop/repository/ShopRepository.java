package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.Shop;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    /** userId로 가게 단건 조회 — 상인 1:1 관계 */
    Optional<Shop> findByUserId(Long userId);

    /** 가게 중복 생성 방지 검사 */
    boolean existsByUserId(Long userId);

    /**
     * 특정 관광지 기반 주변 상점 조회
     * 파라미터: 1=lat, 2=lng, 3=limit
     * TODO: 데이터 증가 시 성능 저하(풀스캔) 우려. lat, lng에 대한 BETWEEN(bounding-box) 조건 추가 검토 필요
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM shops s " +
                   "WHERE s.status = 'ACTIVE' AND s.lat IS NOT NULL AND s.lng IS NOT NULL " +
                   "ORDER BY (6371 * acos(least(1, greatest(-1, cos(radians(?1)) * cos(radians(s.lat)) * cos(radians(s.lng) - radians(?2)) + sin(radians(?1)) * sin(radians(s.lat)))))) ASC " +
                   "LIMIT ?3", nativeQuery = true)
    java.util.List<Shop> findNearbyShops(double lat, double lng, int limit);
}
