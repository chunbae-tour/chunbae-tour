package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.ShopNotice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopNoticeRepository extends JpaRepository<ShopNotice, Long> {

    /** 가게 공지 커서 페이징 — id 내림차순 (최신순). cursorId 미만 항목 조회. */
    List<ShopNotice> findByShopIdAndIdLessThanOrderByIdDesc(Long shopId, Long cursorId, Pageable pageable);

    /** 첫 페이지 조회 (cursor 없을 때). */
    List<ShopNotice> findByShopIdOrderByIdDesc(Long shopId, Pageable pageable);

    /** 소유권 검증용 — shopId + noticeId 조합 조회. */
    Optional<ShopNotice> findByIdAndShopId(Long id, Long shopId);
}
