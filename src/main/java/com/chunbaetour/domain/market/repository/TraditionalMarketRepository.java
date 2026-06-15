package com.chunbaetour.domain.market.repository;

import com.chunbaetour.domain.market.entity.TraditionalMarket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TraditionalMarketRepository extends JpaRepository<TraditionalMarket, Long> {

    /** 시장명 + 주소로 조회 (동명 이장 대응, upsert 키) */
    Optional<TraditionalMarket> findByNameAndAddress(String name, String address);

    /**
     * 운영자 전통시장 목록 검색 (KAN-308) — keyword(시장명 LIKE) + sido 옵션 필터 + id DESC cursor 페이징.
     * keyword/sido null이면 해당 필터 미적용. cursorId null이면 첫 페이지. 와일드카드 이스케이프는 서비스에서 처리(ESCAPE '\').
     * size+1 sentinel로 다음 페이지 존재를 추가 쿼리 없이 판단(AdminShopService.searchForAdmin 패턴 미러).
     */
    @Query("SELECT m FROM TraditionalMarket m WHERE "
            + "(:keyword IS NULL OR m.name LIKE :keyword ESCAPE '\\') "
            + "AND (:sido IS NULL OR m.sido = :sido) "
            + "AND (:cursorId IS NULL OR m.id < :cursorId) "
            + "ORDER BY m.id DESC")
    List<TraditionalMarket> searchForAdmin(@Param("keyword") String keyword,
                                           @Param("sido") String sido,
                                           @Param("cursorId") Long cursorId,
                                           Pageable pageable);
}
