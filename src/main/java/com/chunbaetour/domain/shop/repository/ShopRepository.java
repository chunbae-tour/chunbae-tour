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
     * 특정 관광지 기반 주변 상점 조회 (Bounding Box + Haversine 최적화)
     * 파라미터: 1=lat, 2=lng, 3=radiusKm, 4=limit
     * Bounding box 근사치: 위도 1도는 약 111km, 경도는 대략 111km * cos(lat) 
     * 1km = 약 0.009도
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT *, " +
                   "(6371 * acos(least(1, greatest(-1, cos(radians(?1)) * cos(radians(s.lat)) * cos(radians(s.lng) - radians(?2)) + sin(radians(?1)) * sin(radians(s.lat)))))) AS distance " +
                   "FROM shops s " +
                   "WHERE s.status = 'ACTIVE' AND s.lat IS NOT NULL AND s.lng IS NOT NULL " +
                   "AND s.lat BETWEEN ?1 - (?3 / 111.0) AND ?1 + (?3 / 111.0) " +
                   "AND s.lng BETWEEN ?2 - (?3 / (111.0 * cos(radians(?1)))) AND ?2 + (?3 / (111.0 * cos(radians(?1)))) " +
                   "HAVING distance <= ?3 " +
                   "ORDER BY distance ASC " +
                   "LIMIT ?4", nativeQuery = true)
    java.util.List<Shop> findNearbyShops(double lat, double lng, double radiusKm, int limit);
}
