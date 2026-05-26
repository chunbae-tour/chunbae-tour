package com.chunbaetour.domain.report.repository;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(
            Long reporterId, ReportTargetType targetType, Long targetId);

    // ── 내 신고 내역 cursor 페이징 ────────────────────────────────────────
    List<Report> findByReporterIdOrderByIdDesc(Long reporterId, Pageable pageable);

    List<Report> findByReporterIdAndIdLessThanOrderByIdDesc(
            Long reporterId, Long cursorId, Pageable pageable);
}
