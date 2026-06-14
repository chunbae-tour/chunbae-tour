package com.chunbaetour.domain.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.ReportQueryRepository;
import com.chunbaetour.domain.report.repository.ReportRepository;
import com.chunbaetour.domain.report.type.ReportAction;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link ReportQueryRepository#findByFilter} 통합 테스트 — 동적 필터(status·targetType·reason·reportedUserId) 검증.
 */
@SpringBootTest
class ReportQueryRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ReportQueryRepository queryRepository;
    @Autowired
    private ReportRepository reportRepository;

    @AfterEach
    void cleanup() {
        reportRepository.deleteAll();
    }

    private Report save(long reporterId, ReportTargetType type, long targetId,
                        ReportReason reason, Long reportedUserId, ReportStatus status) {
        Report report = Report.create(reporterId, type, targetId, reason, null, reportedUserId);
        if (status == ReportStatus.RESOLVED) {
            report.resolve(ReportAction.DELETE, "처리", "admin");
        } else if (status == ReportStatus.DISMISSED) {
            report.dismiss(null, "admin");
        }
        return reportRepository.save(report);
    }

    @Test
    @DisplayName("필터 없음 — 전체 반환")
    void noFilter_returnsAll() {
        save(1L, ReportTargetType.POST_FREE, 10L, ReportReason.SPAM, 100L, ReportStatus.PENDING);
        save(2L, ReportTargetType.COMMENT, 11L, ReportReason.SPAM, 100L, ReportStatus.PENDING);

        List<Report> result = queryRepository.findByFilter(null, null, null, null, null, 20);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("targetType 필터 — 해당 타입만")
    void filter_byTargetType() {
        save(1L, ReportTargetType.POST_FREE, 10L, ReportReason.SPAM, 100L, ReportStatus.PENDING);
        save(2L, ReportTargetType.COMMENT, 11L, ReportReason.SPAM, 100L, ReportStatus.PENDING);
        save(3L, ReportTargetType.REVIEW, 12L, ReportReason.SPAM, 100L, ReportStatus.PENDING);

        List<Report> result = queryRepository.findByFilter(
                null, ReportTargetType.COMMENT, null, null, null, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTargetType()).isEqualTo(ReportTargetType.COMMENT);
    }

    @Test
    @DisplayName("status·reason·reportedUserId 복합 필터 — AND 조합")
    void filter_combined() {
        save(1L, ReportTargetType.POST_FREE, 10L, ReportReason.SPAM, 100L, ReportStatus.RESOLVED);     // 대상
        save(2L, ReportTargetType.POST_FREE, 11L, ReportReason.OBSCENE, 100L, ReportStatus.RESOLVED);  // reason 다름
        save(3L, ReportTargetType.POST_FREE, 12L, ReportReason.SPAM, 200L, ReportStatus.RESOLVED);     // reportedUser 다름
        save(4L, ReportTargetType.POST_FREE, 13L, ReportReason.SPAM, 100L, ReportStatus.PENDING);      // status 다름

        List<Report> result = queryRepository.findByFilter(
                ReportStatus.RESOLVED, ReportTargetType.POST_FREE, ReportReason.SPAM, 100L, null, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReporterId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("cursor — 지정 id 미만만, id 내림차순")
    void cursor_pagination() {
        Report r1 = save(1L, ReportTargetType.POST_FREE, 10L, ReportReason.SPAM, 100L, ReportStatus.PENDING);
        Report r2 = save(2L, ReportTargetType.POST_FREE, 11L, ReportReason.SPAM, 100L, ReportStatus.PENDING);
        Report r3 = save(3L, ReportTargetType.POST_FREE, 12L, ReportReason.SPAM, 100L, ReportStatus.PENDING);

        List<Report> result = queryRepository.findByFilter(null, null, null, null, r3.getId(), 20);

        // r3 미만 → r2, r1 (내림차순)
        assertThat(result).extracting(Report::getId).containsExactly(r2.getId(), r1.getId());
    }
}
