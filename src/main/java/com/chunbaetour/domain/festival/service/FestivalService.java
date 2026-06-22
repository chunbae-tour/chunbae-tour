package com.chunbaetour.domain.festival.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.festival.dto.request.FestivalCreateRequest;
import com.chunbaetour.domain.festival.dto.request.FestivalUpdateRequest;
import com.chunbaetour.domain.festival.dto.response.FestivalAdminMutateResponse;
import com.chunbaetour.domain.festival.dto.response.FestivalCacheData;
import com.chunbaetour.domain.festival.dto.response.FestivalCacheList;
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
    public FestivalCacheList findCachedFestivalList(
            LocalDate date, String region, Long cursorId, int size) {
        // 캐시 value의 루트를 배열이 아닌 객체로 만들기 위해 래퍼로 감싼다 — 루트 배열엔 타입정보(@class)를
        // 부착할 수 없어 캐시 HIT 시 LinkedHashMap으로 복원돼 ClassCastException이 나던 문제를 막는다 (KAN-264).
        return FestivalCacheList.of(
                festivalQueryRepository.findActiveByFilter(date, region, cursorId, size + 1)
                        .stream().map(FestivalCacheData::from).toList());
    }

    // ── KAN-97: 사용자 축제 목록 조회 ──────────────────────────────────────

    public CursorPageResponse<FestivalResponse> getList(
            LocalDate date, String region, String cursor, int size) {
        String normalizedRegion = StringUtils.hasText(region) ? region.trim() : null;
        Long cursorId = CursorUtils.decodeSafe(StringUtils.hasText(cursor) ? cursor : null);
        List<FestivalCacheData> rows = self.findCachedFestivalList(date, normalizedRegion, cursorId, size).festivals();

        // 슬라이스·매핑·nextCursor·size echo 공통 진입점 단일화 (KAN-325)
        LocalDate today = LocalDate.now();
        return CursorPageResponse.of(rows, size, d -> d.toResponse(today), FestivalCacheData::id);
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

        // 슬라이스·매핑·nextCursor·size echo 공통 진입점 단일화 (KAN-325)
        LocalDate today = LocalDate.now();
        return CursorPageResponse.of(rows, size, f -> FestivalResponse.of(f, today), Festival::getId);
    }

    // ── KAN-95: 관리자 축제 등록 ───────────────────────────────────────────

    @Transactional
    public FestivalAdminMutateResponse create(FestivalCreateRequest request) {
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
