package com.chunbaetour.domain.report.repository;

import static com.chunbaetour.domain.report.entity.QReport.report;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 관리자 신고 목록 필터 조회 (QueryDSL).
 * status·targetType·reason·reportedUserId 동적 AND 조합 + cursor 페이징.
 */
@Repository
@RequiredArgsConstructor
public class ReportQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * @param cursorId null = 첫 페이지
     * @param size     limit (size+1 전달 — hasNext 판단은 호출자)
     */
    public List<Report> findByFilter(ReportStatus status, ReportTargetType targetType,
            ReportReason reason, Long reportedUserId, Long cursorId, int size) {
        return queryFactory
                .selectFrom(report)
                .where(
                        statusEq(status),
                        targetTypeEq(targetType),
                        reasonEq(reason),
                        reportedUserIdEq(reportedUserId),
                        cursorIdLt(cursorId)
                )
                .orderBy(report.id.desc())
                .limit(size + 1)
                .fetch();
    }

    private BooleanExpression statusEq(ReportStatus status) {
        return status != null ? report.status.eq(status) : null;
    }

    private BooleanExpression targetTypeEq(ReportTargetType targetType) {
        return targetType != null ? report.targetType.eq(targetType) : null;
    }

    private BooleanExpression reasonEq(ReportReason reason) {
        return reason != null ? report.reason.eq(reason) : null;
    }

    private BooleanExpression reportedUserIdEq(Long reportedUserId) {
        return reportedUserId != null ? report.reportedUserId.eq(reportedUserId) : null;
    }

    private BooleanExpression cursorIdLt(Long cursorId) {
        return cursorId != null ? report.id.lt(cursorId) : null;
    }
}
