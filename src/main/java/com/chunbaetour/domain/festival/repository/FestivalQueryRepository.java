package com.chunbaetour.domain.festival.repository;

import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.entity.QFestival;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.chunbaetour.domain.festival.entity.QFestival.festival;

@Repository
@RequiredArgsConstructor
public class FestivalQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 축제 검색 (Phase 2-3)
     *
     * @param keyword 검색어 (옵션)
     * @param startDate 시작일 필터 (옵션)
     * @param endDate 종료일 필터 (옵션)
     * @param region 지역 필터 (옵션)
     * @param cursorId 커서용 마지막 festivalId
     * @param size 페이지 사이즈
     * @return 커서 페이지네이션이 적용된 축제 검색 결과
     */
    public List<Festival> searchFestivals(String keyword, LocalDate startDate, LocalDate endDate, String region, Long cursorId, int size) {
        return queryFactory
                .selectFrom(festival)
                .where(
                        keywordContains(keyword),
                        dateBetween(startDate, endDate),
                        regionEq(region),
                        cursorIdLt(cursorId),
                        festival.status.eq(com.chunbaetour.domain.festival.type.FestivalStatus.ACTIVE)
                )
                .orderBy(festival.id.desc())
                .limit(size + 1)
                .fetch();
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return festival.name.containsIgnoreCase(keyword)
                .or(festival.description.containsIgnoreCase(keyword));
    }

    private BooleanExpression dateBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return null;
        }
        if (startDate != null && endDate != null) {
            // startDate <= festival.endDate AND endDate >= festival.startDate
            return festival.endDate.goe(startDate).and(festival.startDate.loe(endDate));
        }
        if (startDate != null) {
            return festival.endDate.goe(startDate);
        }
        return festival.startDate.loe(endDate);
    }

    private BooleanExpression regionEq(String region) {
        return StringUtils.hasText(region) ? festival.region.eq(region) : null;
    }

    private BooleanExpression cursorIdLt(Long cursorId) {
        return cursorId != null ? festival.id.lt(cursorId) : null;
    }
}
