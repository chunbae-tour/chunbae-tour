package com.chunbaetour.domain.festival.repository;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static com.chunbaetour.domain.festival.entity.QFestival.festival;

@Repository
@RequiredArgsConstructor
public class FestivalQueryRepository {

    private final JPAQueryFactory queryFactory;

    // ── SearchService 전용 (지역/날짜/키워드 복합 검색) ─────────────────────

    /**
     * 축제 검색 (SearchService 전용).
     *
     * @param keyword   검색어 (옵션)
     * @param startDate 시작일 필터 (옵션)
     * @param endDate   종료일 필터 (옵션)
     * @param region    지역 필터 (옵션)
     * @param cursorId  커서용 마지막 festivalId
     * @param size      페이지 사이즈
     */
    public List<Festival> searchFestivals(String keyword, LocalDate startDate, LocalDate endDate,
            String region, Long cursorId, int size) {
        if (size <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return queryFactory
                .selectFrom(festival)
                .where(
                        keywordContains(keyword),
                        dateBetween(startDate, endDate),
                        regionEq(region),
                        cursorIdLt(cursorId),
                        festival.status.eq(FestivalStatus.ACTIVE)
                )
                .orderBy(festival.id.desc())
                .limit(size + 1)
                .fetch();
    }

    // ── FestivalController 전용 (사용자 목록 조회) ────────────────────────

    /**
     * 사용자 축제 목록 조회 — ACTIVE만, 날짜·지역 필터, cursor 페이징.
     *
     * @param dateFilter 해당 날짜가 기간 내에 포함되는 축제 필터 (옵션)
     * @param region     지역 필터 (옵션)
     * @param cursorId   cursor (null = 첫 페이지)
     * @param size       limit (size+1 전달 — hasNext 판단은 호출자)
     */
    public List<Festival> findActiveByFilter(LocalDate dateFilter, String region,
            Long cursorId, int size) {
        return queryFactory
                .selectFrom(festival)
                .where(
                        festival.status.eq(FestivalStatus.ACTIVE),
                        dateContains(dateFilter),
                        regionEq(region),
                        cursorIdLt(cursorId)
                )
                .orderBy(festival.id.desc())
                .limit(size)
                .fetch();
    }

    // ── AdminFestivalController 전용 (관리자 전체 목록 — HIDDEN 포함) ──────

    /**
     * 관리자 축제 목록 — DELETED 제외 전체 (ACTIVE + HIDDEN), cursor 페이징.
     */
    public List<Festival> findNotDeletedByCursor(Long cursorId, int size) {
        return queryFactory
                .selectFrom(festival)
                .where(
                        festival.status.ne(FestivalStatus.DELETED),
                        cursorIdLt(cursorId)
                )
                .orderBy(festival.id.desc())
                .limit(size)
                .fetch();
    }

    // ── CalendarService 전용 ──────────────────────────────────────────────

    /**
     * 월별 캘린더용 — 해당 월과 기간이 겹치는 ACTIVE 축제 전체.
     */
    public List<Festival> findActiveInMonth(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate firstDay = ym.atDay(1);
        LocalDate lastDay = ym.atEndOfMonth();
        return queryFactory
                .selectFrom(festival)
                .where(
                        festival.status.eq(FestivalStatus.ACTIVE),
                        festival.startDate.loe(lastDay),
                        festival.endDate.goe(firstDay)
                )
                .orderBy(festival.startDate.asc(), festival.id.asc())
                .fetch();
    }

    /**
     * 일별 캘린더용 — 해당 날짜를 포함하는 ACTIVE 축제.
     */
    public List<Festival> findActiveOnDate(LocalDate date) {
        return queryFactory
                .selectFrom(festival)
                .where(
                        festival.status.eq(FestivalStatus.ACTIVE),
                        festival.startDate.loe(date),
                        festival.endDate.goe(date)
                )
                .orderBy(festival.startDate.asc(), festival.id.asc())
                .fetch();
    }

    // ── 공통 조건 헬퍼 ────────────────────────────────────────────────────

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        return festival.name.containsIgnoreCase(keyword)
                .or(festival.description.containsIgnoreCase(keyword));
    }

    /**
     * 날짜 범위 조건 (SearchService용).
     * 호출 전 startDate ≤ endDate 검증은 호출자(SearchService) 책임.
     */
    private BooleanExpression dateBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) return null;
        if (startDate != null && endDate != null) {
            return festival.endDate.goe(startDate).and(festival.startDate.loe(endDate));
        }
        if (startDate != null) return festival.endDate.goe(startDate);
        return festival.startDate.loe(endDate);
    }

    /** 단일 날짜가 축제 기간 내 포함되는 조건 (FestivalController용). */
    private BooleanExpression dateContains(LocalDate date) {
        if (date == null) return null;
        return festival.startDate.loe(date).and(festival.endDate.goe(date));
    }

    private BooleanExpression regionEq(String region) {
        return StringUtils.hasText(region) ? festival.region.eq(region) : null;
    }

    private BooleanExpression cursorIdLt(Long cursorId) {
        return cursorId != null ? festival.id.lt(cursorId) : null;
    }
}
