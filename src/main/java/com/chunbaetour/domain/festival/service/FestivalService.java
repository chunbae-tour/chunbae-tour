package com.chunbaetour.domain.festival.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.festival.dto.request.FestivalCreateRequest;
import com.chunbaetour.domain.festival.dto.request.FestivalUpdateRequest;
import com.chunbaetour.domain.festival.dto.response.FestivalAdminMutateResponse;
import com.chunbaetour.domain.festival.dto.response.FestivalCacheData;
import com.chunbaetour.domain.festival.dto.response.FestivalResponse;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalQueryRepository;
import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FestivalService {

    private final FestivalRepository festivalRepository;
    private final FestivalQueryRepository festivalQueryRepository;
    private final FestivalCacheEvictUtil cacheEvict;

    @Lazy
    @Autowired
    private FestivalService self;

    // ── 캐시 레이어 (progressStatus 없는 entity 캐시) ───────────────────────

    @Cacheable(value = "festivals", key = "#festivalId")
    public FestivalCacheData findCachedFestival(Long festivalId) {
        Festival f = festivalRepository.findById(festivalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FESTIVAL_NOT_FOUND));
        return FestivalCacheData.from(f);
    }

    @Cacheable(value = "festivals:list",
            key = "#date + ':' + #region + ':' + #cursorId + ':' + #size")
    public List<FestivalCacheData> findCachedFestivalList(
            LocalDate date, String region, Long cursorId, int size) {
        return festivalQueryRepository.findActiveByFilter(date, region, cursorId, size + 1)
                .stream().map(FestivalCacheData::from).toList();
    }

    // ── KAN-97: 사용자 축제 목록 조회 ──────────────────────────────────────

    public CursorPageResponse<FestivalResponse> getList(
            LocalDate date, String region, String cursor, int size) {
        String normalizedRegion = StringUtils.hasText(region) ? region.trim() : null;
        Long cursorId = CursorUtils.decodeSafe(StringUtils.hasText(cursor) ? cursor : null);
        List<FestivalCacheData> rows = self.findCachedFestivalList(date, normalizedRegion, cursorId, size);

        boolean hasNext = rows.size() > size;
        List<FestivalCacheData> content = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext
                ? CursorUtils.encode(content.get(content.size() - 1).id())
                : null;

        LocalDate today = LocalDate.now();
        return new CursorPageResponse<>(
                content.stream().map(d -> d.toResponse(today)).toList(),
                nextCursor, hasNext, content.size());
    }

    // ── KAN-98: 사용자 축제 상세 조회 ──────────────────────────────────────

    public FestivalResponse getDetail(Long festivalId) {
        FestivalCacheData data = self.findCachedFestival(festivalId);
        if (!data.isActive()) {
            throw new BusinessException(ErrorCode.FESTIVAL_NOT_FOUND);
        }
        LocalDate today = LocalDate.now();
        return data.toResponse(today);
    }

    // ── KAN-95: 관리자 축제 목록 조회 ──────────────────────────────────────

    public CursorPageResponse<FestivalResponse> getAdminList(String cursor, int size) {
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<Festival> rows = festivalQueryRepository.findNotDeletedByCursor(cursorId, size + 1);

        boolean hasNext = rows.size() > size;
        List<Festival> content = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext
                ? CursorUtils.encode(content.get(content.size() - 1).getId())
                : null;

        LocalDate today = LocalDate.now();
        return new CursorPageResponse<>(
                content.stream().map(f -> FestivalResponse.of(f, today)).toList(),
                nextCursor, hasNext, content.size());
    }

    // ── KAN-95: 관리자 축제 등록 ───────────────────────────────────────────

    @Transactional
    public FestivalAdminMutateResponse create(FestivalCreateRequest request) {
        if (request.status() == FestivalStatus.DELETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Festival festival = Festival.create(
                request.name(), request.description(),
                request.region(), request.address(),
                request.startDate(), request.endDate(),
                request.imageUrl(), request.relatedUrl(),
                request.status());
        FestivalAdminMutateResponse response = FestivalAdminMutateResponse.of(festivalRepository.save(festival));
        cacheEvict.evictAll();
        return response;
    }

    // ── KAN-95: 관리자 축제 수정 ───────────────────────────────────────────

    @Transactional
    public FestivalAdminMutateResponse update(Long festivalId, FestivalUpdateRequest request) {
        Festival festival = findForAdmin(festivalId);
        festival.update(
                request.name(), request.description(),
                request.region(), request.address(),
                request.startDate(), request.endDate(),
                request.imageUrl(), request.relatedUrl(),
                request.status());
        FestivalAdminMutateResponse response = FestivalAdminMutateResponse.of(festival);
        cacheEvict.evictById(festivalId);
        return response;
    }

    // ── KAN-95: 관리자 축제 삭제 ───────────────────────────────────────────

    @Transactional
    public void delete(Long festivalId) {
        Festival festival = findForAdmin(festivalId);
        festival.delete();
        cacheEvict.evictById(festivalId);
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────

    private Festival findForAdmin(Long festivalId) {
        Festival festival = festivalRepository.findById(festivalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FESTIVAL_NOT_FOUND));
        if (festival.getStatus() == FestivalStatus.DELETED) {
            throw new BusinessException(ErrorCode.FESTIVAL_DELETED);
        }
        return festival;
    }
}
