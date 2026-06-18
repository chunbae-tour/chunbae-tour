package com.chunbaetour.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FestivalServiceTest {

    @Mock private FestivalRepository festivalRepository;
    @Mock private FestivalQueryRepository festivalQueryRepository;
    @Mock private FestivalCacheEvictUtil cacheEvict;
    @Mock private FestivalService self;

    @InjectMocks private FestivalService festivalService;

    private static final LocalDate START = LocalDate.of(2026, 7, 1);
    private static final LocalDate END   = LocalDate.of(2026, 7, 10);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(festivalService, "self", self);
    }

    // ── create ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create — 올바른 Festival 인자로 save 호출 후 응답 반환 및 캐시 전체 무효화")
    void create_성공() {
        FestivalCreateRequest request = new FestivalCreateRequest(
                "테스트 축제", "축제 설명", "서울", "서울시 강남구 테헤란로 1",
                START, END, null, null, FestivalStatus.ACTIVE
        );
        Festival saved = Festival.create(
                request.name(), request.description(), request.region(), request.address(),
                request.startDate(), request.endDate(), request.imageUrl(), request.relatedUrl(),
                request.status()
        );
        ReflectionTestUtils.setField(saved, "id", 1L);
        // argThat: save에 전달된 Festival 핵심 필드 검증 — 인자 순서 오류 시 탐지
        given(festivalRepository.save(argThat(f ->
                "테스트 축제".equals(f.getName())
                && "서울".equals(f.getRegion())
                && START.equals(f.getStartDate())
                && END.equals(f.getEndDate())
        ))).willReturn(saved);

        FestivalAdminMutateResponse response = festivalService.create(request);

        assertThat(response.festivalId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("테스트 축제");
        assertThat(response.status()).isEqualTo(FestivalStatus.ACTIVE);
        then(cacheEvict).should().evictAll();
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update — 수정 성공 및 festivalId 기준 캐시 evict")
    void update_성공_캐시_evictById() {
        Long id = 1L;
        Festival festival = buildFestival(id, FestivalStatus.ACTIVE);
        given(festivalRepository.findById(id)).willReturn(Optional.of(festival));
        FestivalUpdateRequest request = new FestivalUpdateRequest(
                "수정된 축제", "새 설명", "부산", "부산시 해운대구", START, END, null, null, FestivalStatus.ACTIVE
        );

        FestivalAdminMutateResponse response = festivalService.update(id, request);

        assertThat(response.name()).isEqualTo("수정된 축제");
        then(cacheEvict).should().evictById(id);
    }

    @Test
    @DisplayName("update — 존재하지 않는 festivalId → FESTIVAL_NOT_FOUND")
    void update_없는_festivalId_FESTIVAL_NOT_FOUND() {
        given(festivalRepository.findById(99L)).willReturn(Optional.empty());
        FestivalUpdateRequest request = new FestivalUpdateRequest(
                "수정됨", null, "서울", "서울시", START, END, null, null, FestivalStatus.ACTIVE
        );

        assertThatThrownBy(() -> festivalService.update(99L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FESTIVAL_NOT_FOUND);
    }

    @Test
    @DisplayName("update — DELETED 상태 축제 수정 시도 → FESTIVAL_DELETED")
    void update_DELETED_축제_FESTIVAL_DELETED() {
        Long id = 1L;
        Festival festival = buildFestival(id, FestivalStatus.ACTIVE);
        festival.delete();
        given(festivalRepository.findById(id)).willReturn(Optional.of(festival));
        FestivalUpdateRequest request = new FestivalUpdateRequest(
                "수정됨", null, "서울", "서울시", START, END, null, null, FestivalStatus.ACTIVE
        );

        assertThatThrownBy(() -> festivalService.update(id, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FESTIVAL_DELETED);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete — soft delete 성공, status=DELETED 확인 및 캐시 evict")
    void delete_성공_캐시_evict() {
        Long id = 1L;
        Festival festival = buildFestival(id, FestivalStatus.ACTIVE);
        given(festivalRepository.findById(id)).willReturn(Optional.of(festival));

        festivalService.delete(id);

        // JPA dirty-check 의존: @Transactional 내부에서 festival.delete()를 호출하므로
        // 트랜잭션 커밋 시 DB에 반영됨. 단위 테스트에서는 인메모리 상태로만 검증.
        assertThat(festival.getStatus()).isEqualTo(FestivalStatus.DELETED);
        then(cacheEvict).should().evictById(id);
    }

    @Test
    @DisplayName("delete — 존재하지 않는 festivalId → FESTIVAL_NOT_FOUND")
    void delete_없는_festivalId_FESTIVAL_NOT_FOUND() {
        given(festivalRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> festivalService.delete(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FESTIVAL_NOT_FOUND);
    }

    @Test
    @DisplayName("delete — DELETED 상태 축제 재삭제 시도 → FESTIVAL_DELETED")
    void delete_DELETED_축제_재삭제_FESTIVAL_DELETED() {
        Long id = 1L;
        Festival festival = buildFestival(id, FestivalStatus.ACTIVE);
        festival.delete();
        given(festivalRepository.findById(id)).willReturn(Optional.of(festival));

        assertThatThrownBy(() -> festivalService.delete(id))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FESTIVAL_DELETED);
    }

    // ── getDetail ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getDetail — ACTIVE 축제 정상 반환")
    void getDetail_ACTIVE_성공() {
        Long festivalId = 1L;
        FestivalCacheData activeData = buildCacheData(festivalId, FestivalStatus.ACTIVE);
        given(self.findCachedFestival(festivalId)).willReturn(activeData);

        FestivalResponse response = festivalService.getDetail(festivalId);

        assertThat(response.festivalId()).isEqualTo(festivalId);
        assertThat(response.name()).isEqualTo("축제1");
        assertThat(response.status()).isEqualTo(FestivalStatus.ACTIVE);
    }

    @Test
    @DisplayName("getDetail — DELETED 상태 축제 조회 시 FESTIVAL_NOT_FOUND (존재 여부 노출 차단)")
    void getDetail_DELETED_축제_FESTIVAL_NOT_FOUND() {
        Long festivalId = 2L;
        FestivalCacheData deletedData = buildCacheData(festivalId, FestivalStatus.DELETED);
        given(self.findCachedFestival(festivalId)).willReturn(deletedData);

        assertThatThrownBy(() -> festivalService.getDetail(festivalId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FESTIVAL_NOT_FOUND);
    }

    @Test
    @DisplayName("getDetail — HIDDEN 상태 축제 조회 시 FESTIVAL_NOT_FOUND (사용자 미노출)")
    void getDetail_HIDDEN_축제_FESTIVAL_NOT_FOUND() {
        Long festivalId = 3L;
        FestivalCacheData hiddenData = buildCacheData(festivalId, FestivalStatus.HIDDEN);
        given(self.findCachedFestival(festivalId)).willReturn(hiddenData);

        assertThatThrownBy(() -> festivalService.getDetail(festivalId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FESTIVAL_NOT_FOUND);
    }

    // ── getAdminList ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getAdminList — size+1 결과이면 hasNext=true, nextCursor 존재")
    void getAdminList_hasNext_true() {
        int size = 2;
        // CursorUtils.decodeSafe(null) → null 보장 (소스: CursorUtils:70 if(cursor==null) return null)
        given(festivalQueryRepository.findNotDeletedByCursor(null, size + 1))
                .willReturn(List.of(buildFestival(1L, FestivalStatus.ACTIVE),
                                    buildFestival(2L, FestivalStatus.ACTIVE),
                                    buildFestival(3L, FestivalStatus.HIDDEN)));

        CursorPageResponse<FestivalResponse> result = festivalService.getAdminList(null, size);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
        assertThat(result.content()).hasSize(size);
    }

    @Test
    @DisplayName("getAdminList — size 이하 결과이면 hasNext=false, nextCursor null")
    void getAdminList_hasNext_false() {
        int size = 5;
        given(festivalQueryRepository.findNotDeletedByCursor(null, size + 1))
                .willReturn(List.of(buildFestival(1L, FestivalStatus.ACTIVE)));

        CursorPageResponse<FestivalResponse> result = festivalService.getAdminList(null, size);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.content()).hasSize(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Festival buildFestival(Long id, FestivalStatus status) {
        Festival f = Festival.create("축제" + id, null, "서울", "서울시", START, END, null, null, status);
        ReflectionTestUtils.setField(f, "id", id);
        return f;
    }

    private FestivalCacheData buildCacheData(Long id, FestivalStatus status) {
        return new FestivalCacheData(id, "축제" + id, null, "서울", "서울시", START, END, null, null, null, null, status);
    }
}
