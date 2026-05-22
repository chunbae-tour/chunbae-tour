package com.chunbaetour.domain.festival.repository;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.entity.QFestival;
import com.querydsl.core.types.dsl.BooleanExpression;
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

    /**
     * 날짜 범위 조건을 생성한다.
     * <p>
     * startDate/endDate 역순 유효성 검증({@code startDate.isAfter(endDate)})은
     * 호출자인 {@link com.chunbaetour.domain.search.service.SearchService}에서
     * {@code SEARCH_INVALID_DATE_RANGE}로 사전 검증 후 이 메서드를 호출하므로
     * 여기서는 중복 검증하지 않는다.
     * </p>
     * <ul>
     *   <li>both null   → 조건 없음 (전체 조회)</li>
     *   <li>both set    → 기간 겹침 조건 (startDate ≤ endDate AND festival.startDate ≤ endDate)</li>
     *   <li>startDate only → festival.endDate ≥ startDate (시작일 이후 종료되는 축제)</li>
     *   <li>endDate only   → festival.startDate ≤ endDate (종료일 이전 시작하는 축제)</li>
     * </ul>
     */
    private BooleanExpression dateBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return null;
        }
        if (startDate != null && endDate != null) {
            // 기간 겹침 조건: festival의 기간이 [startDate, endDate]와 교차하는 경우를 필터링한다.
            // (festival.startDate <= endDate) AND (festival.endDate >= startDate)
            return festival.endDate.goe(startDate).and(festival.startDate.loe(endDate));
        }
        if (startDate != null) {
            // startDate만 지정: startDate 이후 종료되는 축제 (진행 중 + 예정 포함)
            return festival.endDate.goe(startDate);
        }
        // endDate만 지정: endDate 이전 시작한 축제 (진행 중 + 종료 포함)
        return festival.startDate.loe(endDate);
    }

    private BooleanExpression regionEq(String region) {
        return StringUtils.hasText(region) ? festival.region.eq(region) : null;
    }

    private BooleanExpression cursorIdLt(Long cursorId) {
        return cursorId != null ? festival.id.lt(cursorId) : null;
    }
}
