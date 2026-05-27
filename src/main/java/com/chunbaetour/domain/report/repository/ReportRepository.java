package com.chunbaetour.domain.report.repository;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // ── 목록 조회 (cursor 페이징) ─────────────────────────────────────────

    @Query("SELECT r FROM Report r ORDER BY r.id DESC")
    List<Report> findAllOrderByIdDesc(Pageable pageable);

    @Query("SELECT r FROM Report r WHERE r.id < :cursorId ORDER BY r.id DESC")
    List<Report> findByIdLessThanOrderByIdDesc(@Param("cursorId") Long cursorId, Pageable pageable);

    @Query("SELECT r FROM Report r WHERE r.status = :status ORDER BY r.id DESC")
    List<Report> findByStatusOrderByIdDesc(@Param("status") ReportStatus status, Pageable pageable);

    @Query("SELECT r FROM Report r WHERE r.status = :status AND r.id < :cursorId ORDER BY r.id DESC")
    List<Report> findByStatusAndIdLessThanOrderByIdDesc(
            @Param("status") ReportStatus status,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // ── 내 신고 내역 cursor 페이징 (KAN-90) ─────────────────────────────

    List<Report> findByReporterIdOrderByIdDesc(Long reporterId, Pageable pageable);

    List<Report> findByReporterIdAndIdLessThanOrderByIdDesc(
            Long reporterId, Long cursorId, Pageable pageable);

    // ── 중복 신고 체크 (REPORT_001) ──────────────────────────────────────

    boolean existsByReporterIdAndTargetTypeAndTargetId(
            Long reporterId, ReportTargetType targetType, Long targetId);

}
