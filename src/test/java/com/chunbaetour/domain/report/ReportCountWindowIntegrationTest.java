package com.chunbaetour.domain.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.ReportRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 제재 카운트 1년 윈도우 쿼리 통합 테스트 — `resolved_at >= since` 필터 실동작 검증.
 */
@SpringBootTest
class ReportCountWindowIntegrationTest extends AbstractIntegrationTest {

    private static final long REPORTED_USER = 7000L;

    @Autowired
    private ReportRepository reportRepository;

    @AfterEach
    void cleanup() {
        reportRepository.deleteAll();
    }

    private void saveResolvedReport(long reporterId, LocalDateTime resolvedAt) {
        Report report = Report.create(reporterId, ReportTargetType.POST_FREE, 999L,
                ReportReason.SPAM, null, REPORTED_USER);
        report.resolve(com.chunbaetour.domain.report.type.ReportAction.DELETE, "처리", "admin");
        // resolved_at을 테스트 시점으로 강제 (resolve()는 now()로 세팅하므로)
        ReflectionTestUtils.setField(report, "resolvedAt", resolvedAt);
        reportRepository.save(report);
    }

    @Test
    @DisplayName("1년 윈도우 — 1년 지난 RESOLVED 신고는 카운트에서 제외")
    void windowedCount_excludes_old_reports() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusYears(1);

        saveResolvedReport(1L, now.minusDays(10));     // 윈도우 안
        saveResolvedReport(2L, now.minusMonths(6));    // 윈도우 안
        saveResolvedReport(3L, now.minusYears(2));     // 윈도우 밖 (2년 전)

        long windowed = reportRepository
                .countByReportedUserIdAndTargetTypeAndStatusAndResolvedAtGreaterThanEqual(
                        REPORTED_USER, ReportTargetType.POST_FREE, ReportStatus.RESOLVED, since);

        assertThat(windowed).isEqualTo(2);  // 2년 전 1건 제외
    }
}
