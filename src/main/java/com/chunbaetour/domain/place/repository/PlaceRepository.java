package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /** 
     * 4-1. 인기 관광지 Fallback: ACTIVE 상태의 관광지를 가중치(찜, 조회수) 내림차순 정렬
     */
    @Query("SELECT p FROM Place p WHERE p.status = 'ACTIVE' ORDER BY (cast(p.likeCount as double) * :likeWeight + cast(p.viewCount as double) * :viewWeight) DESC")
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
                   "AND (6371 * acos(least(1, greatest(-1, cos(radians(?1)) * cos(radians(p.lat)) * cos(radians(p.lng) - radians(?2)) + sin(radians(?1)) * sin(radians(p.lat)))))) <= ?3 " +
                   "ORDER BY (6371 * acos(least(1, greatest(-1, cos(radians(?1)) * cos(radians(p.lat)) * cos(radians(p.lng) - radians(?2)) + sin(radians(?1)) * sin(radians(p.lat)))))) ASC " +
                   "LIMIT ?4", nativeQuery = true)
    List<Place> findNearbyPlacesWithinRadius(double lat, double lng, double radiusKm, int limit);

    /**
     * 4-2. 특정 관광지 기반 추천 (동일 카테고리 + 근거리 TOP N)
     * 파라미터: 1=lat, 2=lng, 3=category(String), 4=excludePlaceId, 5=limit
     * TODO: 데이터 증가 시 성능 저하(풀스캔) 우려. lat, lng에 대한 BETWEEN(bounding-box) 조건 추가 검토 필요
     */
    @Query(value = "SELECT * FROM places p " +
                   "WHERE p.status = 'ACTIVE' AND p.category = ?3 AND p.id != ?4 " +
                   "ORDER BY (6371 * acos(least(1, greatest(-1, cos(radians(?1)) * cos(radians(p.lat)) * cos(radians(p.lng) - radians(?2)) + sin(radians(?1)) * sin(radians(p.lat)))))) ASC " +
                   "LIMIT ?5", nativeQuery = true)
    List<Place> findNearbyPlacesByCategory(double lat, double lng, String category, Long excludePlaceId, int limit);

    /**
     * 상태 기반 단건 관광지 조회
     */
    Optional<Place> findByIdAndStatus(Long id, PlaceStatus status);

    /**
     * 특정 상태가 아닌 Place 존재 여부 (KAN-217 Shop-Place 연결 검증용).
     * DELETED를 제외하고 조회 — soft delete된 장소에 가게 연결 차단.
     */
    boolean existsByIdAndStatusNot(Long id, PlaceStatus status);

    /**
     * 상태 기반 단건 관광지 조회 (비관적 쓰기 락 - 동시성 제어용)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Place p WHERE p.id = :id AND p.status = :status")
    Optional<Place> findByIdAndStatusForUpdate(@Param("id") Long id, @Param("status") PlaceStatus status);

    /**
     * 운영자 관광지 목록 검색 — keyword(이름 부분일치) + category + cursor 페이징 (Admin Epic KAN-177 S07).
     *
     * <p>모든 필터는 {@code null}이면 미적용(전체). cursor는 id 내림차순 keyset 페이징 —
     * {@code cursorId}보다 작은 id만 조회해 다음 페이지를 sentinel(size+1) 방식으로 판단(서비스 책임).
     *
     * <p>{@code DELETED}(soft delete) 상태는 운영자 목록에서 제외하고 ACTIVE/HIDDEN만 노출한다 — 사용자 검색은
     * 별도로 status=ACTIVE 고정 필터를 적용(PlaceQueryRepository.searchByKeyword). keyword는
     * {@code LIKE '%keyword%'}이며 공백 문자열은 호출자(서비스)가 null로 정규화해 전달.
     */
    @Query("SELECT p FROM Place p WHERE "
            + "p.status <> com.chunbaetour.domain.place.type.PlaceStatus.DELETED "
            + "AND (:keyword IS NULL OR p.name LIKE CONCAT('%', :keyword, '%') ESCAPE '\\') "
            + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:cursorId IS NULL OR p.id < :cursorId) "
            + "ORDER BY p.id DESC")
    List<Place> searchForAdmin(@Param("keyword") String keyword,
                               @Param("category") PlaceCategory category,
                               @Param("cursorId") Long cursorId,
                               Pageable pageable);

    /**
     * 운영자 대시보드용 전체 관광지 수 — soft delete(DELETED) 제외(S07 리뷰 H).
     * 사용자에게 노출되는 ACTIVE/HIDDEN만 집계한다.
     */
    long countByStatusNot(PlaceStatus status);
}
