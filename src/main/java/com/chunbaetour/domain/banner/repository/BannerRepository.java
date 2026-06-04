package com.chunbaetour.domain.banner.repository;

import com.chunbaetour.domain.banner.Banner;
import com.chunbaetour.domain.banner.type.BannerStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    /**
     * 운영자 배너 목록 검색 — status 필터 + (priority ASC, id DESC) keyset 페이징 (Admin Epic KAN-177 S09).
     *
     * <p>{@code DELETED}(soft delete)는 항상 제외하고 ACTIVE/HIDDEN만 노출한다. {@code status} 파라미터가
     * 지정되면 해당 상태만, null이면 DELETED 외 전체.
     *
     * <p>정렬 키가 (priority ASC, id DESC) 복합이므로 cursor도 (priority, id) 복합 keyset이다. 다음 페이지는
     * "priority가 더 크거나 / priority가 같고 id가 더 작은" 행 — {@code cursorPriority}/{@code cursorId}는
     * 둘 다 null(첫 페이지)이거나 둘 다 non-null(서비스가 보장). size+1 sentinel로 hasNext는 서비스가 판단.
     */
    @Query("SELECT b FROM Banner b WHERE "
            + "b.status <> com.chunbaetour.domain.banner.type.BannerStatus.DELETED "
            + "AND (:status IS NULL OR b.status = :status) "
            + "AND (:cursorPriority IS NULL "
            + "     OR b.priority > :cursorPriority "
            + "     OR (b.priority = :cursorPriority AND b.id < :cursorId)) "
            + "ORDER BY b.priority ASC, b.id DESC")
    List<Banner> searchForAdmin(@Param("status") BannerStatus status,
                                @Param("cursorPriority") Integer cursorPriority,
                                @Param("cursorId") Long cursorId,
                                Pageable pageable);

    /**
     * 운영자 대시보드용 전체 배너 수 — soft delete(DELETED) 제외(S10 대시보드 의존).
     * ACTIVE/HIDDEN만 집계한다.
     */
    long countByStatusNot(BannerStatus status);

    /**
     * 노출 중(ACTIVE) 배너 수 — S10 대시보드 의존. 사용자에게 실제 노출되는 배너 기준
     * (HIDDEN/DELETED 제외)으로, 운영자 배너 노출 정책과 일치한다.
     */
    long countByStatus(BannerStatus status);
}
