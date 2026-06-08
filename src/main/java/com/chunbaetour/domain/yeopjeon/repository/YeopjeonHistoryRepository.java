package com.chunbaetour.domain.yeopjeon.repository;

import com.chunbaetour.domain.yeopjeon.entity.YeopjeonHistory;
import com.chunbaetour.domain.yeopjeon.type.YeopjeonHistoryType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 엽전 이력 cursor 페이징 조회 리포지토리.
 * cursor = id 기반 keyset 페이징 (OFFSET 미사용).
 * Pageable은 size+1 요청으로 hasNext 여부 판단.
 */
public interface YeopjeonHistoryRepository extends JpaRepository<YeopjeonHistory, Long> {

    // cursor 있을 때: cursorId보다 작은 id를 최신순 조회
    @Query("SELECT h FROM YeopjeonHistory h WHERE h.userId = :userId AND h.id < :cursorId ORDER BY h.id DESC")
    List<YeopjeonHistory> findByUserIdAndIdLessThanOrderByIdDesc(
            @Param("userId") Long userId,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // cursor 없을 때 (첫 페이지): 전체 최신순 조회
    @Query("SELECT h FROM YeopjeonHistory h WHERE h.userId = :userId ORDER BY h.id DESC")
    List<YeopjeonHistory> findByUserIdOrderByIdDesc(
            @Param("userId") Long userId,
            Pageable pageable
    );

    Optional<YeopjeonHistory> findFirstByPaymentOrderIdAndTypeOrderByIdAsc(
            Long paymentOrderId,
            YeopjeonHistoryType type
    );

    boolean existsByUserIdAndIdGreaterThanAndTypeIn(
            Long userId,
            Long id,
            List<YeopjeonHistoryType> types
    );
}
