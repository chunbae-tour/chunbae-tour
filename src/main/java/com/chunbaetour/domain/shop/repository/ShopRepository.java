package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.type.ShopStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    /** userId로 내 가게 목록 전체 조회 — 다중 가게 지원 */
    List<Shop> findAllByUserId(Long userId);

    /**
     * 운영자 가게 목록 검색 (KAN-307) — keyword(가게명 LIKE) + status 옵션 필터 + id DESC cursor 페이징.
     * keyword/status null이면 해당 필터 미적용. cursorId null이면 첫 페이지. 와일드카드 이스케이프는 서비스에서 처리(ESCAPE '\').
     * size+1 sentinel로 다음 페이지 존재를 추가 쿼리 없이 판단(AdminPlaceService.searchForAdmin 패턴 미러).
     */
    @Query("SELECT s FROM Shop s WHERE "
            + "(:keyword IS NULL OR s.shopName LIKE :keyword ESCAPE '\\') "
            + "AND (:status IS NULL OR s.status = :status) "
            + "AND (:cursorId IS NULL OR s.id < :cursorId) "
            + "ORDER BY s.id DESC")
    List<Shop> searchForAdmin(@Param("keyword") String keyword,
                              @Param("status") ShopStatus status,
                              @Param("cursorId") Long cursorId,
                              Pageable pageable);

    /** placeId 목록으로 소속 가게 조회 — 통합검색 Place→Shop 연결 (KAN-217). */
    List<Shop> findByPlaceIdIn(Collection<Long> placeIds);

    /** Place soft delete 시 연관 shop.place_id를 null로 일괄 처리 — ON DELETE SET NULL이 DB에서 발화하지 않으므로 서비스 레이어에서 직접 처리 (KAN-362). */
    @Modifying
    @Query("UPDATE Shop s SET s.placeId = null WHERE s.placeId = :placeId")
    void clearPlaceReference(@Param("placeId") Long placeId);

    /** 상태별 가게 수 — S06 대시보드 카운트(AdminShopService.getSuspendedShops) 의존. */
    long countByStatus(ShopStatus status);

    /** 소유권 검증용 — shopId + userId 조합으로 본인 가게인지 확인 */
    Optional<Shop> findByIdAndUserId(Long id, Long userId);

    /**
     * 광고 신청 중복 방지용 직렬화 락.
     * Shop 행을 SELECT FOR UPDATE로 잠가 동시 신청의 PENDING 중복 체크를 원자적으로 보장.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shop s WHERE s.id = :id")
    Optional<Shop> findByIdWithLock(@Param("id") Long id);

    /**
     * 본인 가게 직렬화 락 (KAN-253 재발급-결제요청 nonce race 방어).
     * shopId + userId로 본인 가게 행을 SELECT FOR UPDATE로 잠금.
     * QR 재발급(nonce 회전)과 결제요청 nonce 검증(findByIdWithLock)이 동일 행을 잠가
     * ms 단위로 겹치는 경우에도 옛 nonce 요청이 통과하지 못하도록 직렬화.
     * userId 불일치(타인 가게) 시 빈 결과 → 호출부에서 SHOP_NOT_FOUND.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shop s WHERE s.id = :id AND s.userId = :userId")
    Optional<Shop> findByIdAndUserIdWithLock(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 특정 관광지 기반 주변 상점 조회 (Bounding Box + Haversine 최적화)
     * 파라미터: 1=lat, 2=lng, 3=radiusKm, 4=limit
     * Bounding box 근사치: 위도 1도는 약 111km, 경도는 대략 111km * cos(lat)
     * 1km = 약 0.009도
     */
    @Query(value = "SELECT *, " +
                   "(6371 * acos(least(1, greatest(-1, cos(radians(?1)) * cos(radians(s.lat)) * cos(radians(s.lng) - radians(?2)) + sin(radians(?1)) * sin(radians(s.lat)))))) AS distance " +
                   "FROM shops s " +
                   "WHERE s.status = 'ACTIVE' AND s.lat IS NOT NULL AND s.lng IS NOT NULL " +
                   "AND s.lat BETWEEN ?1 - (?3 / 111.0) AND ?1 + (?3 / 111.0) " +
                   "AND s.lng BETWEEN ?2 - (?3 / (111.0 * cos(radians(?1)))) AND ?2 + (?3 / (111.0 * cos(radians(?1)))) " +
                   "HAVING distance <= ?3 " +
                   "ORDER BY distance ASC " +
                   "LIMIT ?4", nativeQuery = true)
    List<Shop> findNearbyShops(double lat, double lng, double radiusKm, int limit);
}
