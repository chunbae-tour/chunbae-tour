package com.chunbaetour.domain.community.companion.repository;

import static com.chunbaetour.domain.community.companion.entity.QCompanionPost.companionPost;

import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 동행 게시글 목록 cursor 조회 (QueryDSL).
 *
 * <p>region·meetingDate는 동적 필터다. JPQL의 {@code (:param IS NULL OR col = :param)} 방식은
 * 옵티마이저가 복합 인덱스를 안정적으로 못 타고 status range + residual filter로 남을 수 있어,
 * QueryDSL로 null 필터 조건 자체를 SQL에서 제거한다. → 필터가 있을 때 {@code (status, region)} /
 * {@code (status, meeting_date)} 인덱스를 그대로 사용한다.
 */
@Repository
@RequiredArgsConstructor
public class CompanionPostQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<CompanionPost> findByFilters(
            CompanionPostStatus status, String region, LocalDate meetingDate, Long cursorId, int limit) {
        return queryFactory
                .selectFrom(companionPost)
                .where(
                        companionPost.status.eq(status),
                        regionEq(region),
                        meetingDateEq(meetingDate),
                        cursorIdLt(cursorId)
                )
                .orderBy(companionPost.id.desc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression regionEq(String region) {
        return StringUtils.hasText(region) ? companionPost.region.eq(region) : null;
    }

    private BooleanExpression meetingDateEq(LocalDate meetingDate) {
        return meetingDate != null ? companionPost.meetingDate.eq(meetingDate) : null;
    }

    private BooleanExpression cursorIdLt(Long cursorId) {
        return cursorId != null ? companionPost.id.lt(cursorId) : null;
    }
}
