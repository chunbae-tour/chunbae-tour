package com.chunbaetour.domain.festival.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.festival.dto.request.FestivalCreateRequest;
import com.chunbaetour.domain.festival.dto.request.FestivalUpdateRequest;
import com.chunbaetour.domain.festival.dto.response.FestivalAdminMutateResponse;
import com.chunbaetour.domain.festival.dto.response.FestivalResponse;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalQueryRepository;
import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 축제 서비스 (KAN-95/97/98).
 * 캘린더 관련 로직은 CalendarService 참조.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FestivalService {

    private final FestivalRepository festivalRepository;
    private final FestivalQueryRepository festivalQueryRepository;

    // ── KAN-97: 사용자 축제 목록 조회 ──────────────────────────────────────

    /**
     * 사용자 축제 목록 조회 — ACTIVE만, date/region 필터, cursor 페이징.
     *
     * @param date   해당 날짜 기간 내 포함되는 축제 필터 (null = 전체)
     * @param region 지역 필터 (null = 전체)
     * @param cursor Base64 인코딩된 cursor (null = 첫 페이지)
     * @param size   페이지 크기
     */
    @Cacheable(value = "festivals:list",
            key = "#date + ':' + #region + ':' + #cursor + ':' + #size")
    public CursorPageResponse<FestivalResponse> getList(
            LocalDate date, String region, String cursor, int size) {
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<Festival> rows = festivalQueryRepository.findActiveByFilter(
                date, region, cursorId, size + 1);

        boolean hasNext = rows.size() > size;
        List<Festival> content = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext
                ? CursorUtils.encode(content.get(content.size() - 1).getId())
                : null;

        return new CursorPageResponse<>(
                content.stream().map(FestivalResponse::of).toList(),
                nextCursor, hasNext, content.size());
    }

    // ── KAN-98: 사용자 축제 상세 조회 ──────────────────────────────────────

    /**
     * 사용자 축제 상세 조회 — ACTIVE 축제만.
     */
    @Cacheable(value = "festivals", key = "#festivalId")
    public FestivalResponse getDetail(Long festivalId) {
        Festival festival = festivalRepository.findById(festivalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FESTIVAL_NOT_FOUND));
        if (festival.getStatus() == FestivalStatus.DELETED) {
            throw new BusinessException(ErrorCode.FESTIVAL_DELETED);
        }
        if (!festival.isActive()) {
            // HIDDEN — 사용자에게는 존재하지 않는 것으로 처리
            throw new BusinessException(ErrorCode.FESTIVAL_NOT_FOUND);
        }
        return FestivalResponse.of(festival);
    }

    // ── KAN-95: 관리자 축제 목록 조회 ──────────────────────────────────────

    /**
     * 관리자 축제 목록 — DELETED 제외 (ACTIVE + HIDDEN), cursor 페이징.
     */
    public CursorPageResponse<FestivalResponse> getAdminList(String cursor, int size) {
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<Festival> rows = festivalQueryRepository.findNotDeletedByCursor(cursorId, size + 1);

        boolean hasNext = rows.size() > size;
        List<Festival> content = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext
                ? CursorUtils.encode(content.get(content.size() - 1).getId())
                : null;

        return new CursorPageResponse<>(
                content.stream().map(FestivalResponse::of).toList(),
                nextCursor, hasNext, content.size());
    }

    // ── KAN-95: 관리자 축제 등록 ───────────────────────────────────────────

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "festivals", allEntries = true),
            @CacheEvict(value = "festivals:list", allEntries = true),
            @CacheEvict(value = "calendar:monthly", allEntries = true),
            @CacheEvict(value = "calendar:daily", allEntries = true)
    })
    public FestivalAdminMutateResponse create(FestivalCreateRequest request) {
        validateDateRange(request.startDate(), request.endDate());
        Festival festival = Festival.create(
                request.name(), request.description(),
                request.region(), request.address(),
                request.startDate(), request.endDate(),
                request.imageUrl(), request.relatedUrl(),
                request.status());
        return FestivalAdminMutateResponse.of(festivalRepository.save(festival));
    }

    // ── KAN-95: 관리자 축제 수정 ───────────────────────────────────────────

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "festivals", key = "#festivalId"),
            @CacheEvict(value = "festivals:list", allEntries = true),
            @CacheEvict(value = "calendar:monthly", allEntries = true),
            @CacheEvict(value = "calendar:daily", allEntries = true)
    })
    public FestivalAdminMutateResponse update(Long festivalId, FestivalUpdateRequest request) {
        validateDateRange(request.startDate(), request.endDate());
        Festival festival = findForAdmin(festivalId);
        festival.update(
                request.name(), request.description(),
                request.region(), request.address(),
                request.startDate(), request.endDate(),
                request.imageUrl(), request.relatedUrl(),
                request.status());
        return FestivalAdminMutateResponse.of(festival);
    }

    // ── KAN-95: 관리자 축제 삭제 ───────────────────────────────────────────

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "festivals", key = "#festivalId"),
            @CacheEvict(value = "festivals:list", allEntries = true),
            @CacheEvict(value = "calendar:monthly", allEntries = true),
            @CacheEvict(value = "calendar:daily", allEntries = true)
    })
    public void delete(Long festivalId) {
        Festival festival = findForAdmin(festivalId);
        festival.delete();
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────

    /** 관리자용 조회 — DELETED 포함 (처리 가능하도록). */
    private Festival findForAdmin(Long festivalId) {
        Festival festival = festivalRepository.findById(festivalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FESTIVAL_NOT_FOUND));
        if (festival.getStatus() == FestivalStatus.DELETED) {
            throw new BusinessException(ErrorCode.FESTIVAL_DELETED);
        }
        return festival;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
