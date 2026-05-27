package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.type.PlaceCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /** 
     * 4-1. 인기 관광지 Fallback: ACTIVE 상태의 관광지를 가중치(찜, 조회수) 내림차순 정렬
     */
    @Query("SELECT p FROM Place p WHERE p.status = 'ACTIVE' ORDER BY (p.likeCount * :likeWeight + p.viewCount * :viewWeight) DESC")
    List<Place> findTopPopularPlaces(@Param("likeWeight") double likeWeight, @Param("viewWeight") double viewWeight, Pageable pageable);

    /**
     * 4-1. 카테고리별 추천: 카테고리 일치 & 평점 내림차순
     */
    @Query("SELECT p FROM Place p WHERE p.category = :category AND p.status = 'ACTIVE' ORDER BY p.rating DESC")
    List<Place> findTopByCategory(@Param("category") PlaceCategory category, Pageable pageable);

    /**
     * 4-1. 반경 내 주변 관광지 조회 Fallback (Native Query)
     * 시니어 아키텍트 리뷰 반영: ORDER BY RAND()는 풀 테이블 스캔을 유발하므로 제거.
     * DB에서는 반경 내 데이터를 거리순(또는 기본순)으로 넉넉히 가져온 뒤, Service 단에서 메모리 셔플 후 샘플링하도록 변경.
     * TODO: MySQL 8.0 이상에서는 Haversine 수식 대신 ST_Distance_Sphere()를 활용해 인덱스를 태우고 코드를 간결히 하는 방향으로 리팩토링(Cleanup) 필요.
     */
    @Query(value = "SELECT * FROM places p " +
                   "WHERE p.status = 'ACTIVE' " +
                   "AND (6371 * acos(cos(radians(?1)) * cos(radians(p.lat)) * cos(radians(p.lng) - radians(?2)) + sin(radians(?1)) * sin(radians(p.lat)))) <= ?3 " +
                   "ORDER BY (6371 * acos(cos(radians(?1)) * cos(radians(p.lat)) * cos(radians(p.lng) - radians(?2)) + sin(radians(?1)) * sin(radians(p.lat)))) ASC " +
                   "LIMIT ?4", nativeQuery = true)
    List<Place> findNearbyPlacesWithinRadius(double lat, double lng, double radiusKm, int limit);

    /**
     * 4-2. 특정 관광지 기반 추천 (동일 카테고리 + 근거리 TOP N)
     * 파라미터: 1=lat, 2=lng, 3=category(String), 4=excludePlaceId, 5=limit
     */
    @Query(value = "SELECT * FROM places p " +
                   "WHERE p.status = 'ACTIVE' AND p.category = ?3 AND p.id != ?4 " +
                   "ORDER BY (6371 * acos(cos(radians(?1)) * cos(radians(p.lat)) * cos(radians(p.lng) - radians(?2)) + sin(radians(?1)) * sin(radians(p.lat)))) ASC " +
                   "LIMIT ?5", nativeQuery = true)
    List<Place> findNearbyPlacesByCategory(double lat, double lng, PlaceCategory category, Long excludePlaceId, int limit);
}
