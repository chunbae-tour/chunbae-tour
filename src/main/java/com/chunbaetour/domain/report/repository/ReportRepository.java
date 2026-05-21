package com.chunbaetour.domain.report.repository;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(
            Long reporterId, ReportTargetType targetType, Long targetId);
}
