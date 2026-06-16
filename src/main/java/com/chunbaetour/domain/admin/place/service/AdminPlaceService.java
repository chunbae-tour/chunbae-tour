package com.chunbaetour.domain.admin.place.service;

import com.chunbaetour.domain.admin.place.dto.request.AdminPlaceCreateRequest;
import com.chunbaetour.domain.admin.place.dto.request.AdminPlaceUpdateRequest;
import com.chunbaetour.domain.admin.place.dto.response.AdminPlaceDetailResponse;
import com.chunbaetour.domain.admin.place.dto.response.AdminPlaceListResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 운영자 관광지/전통시장 관리 서비스 (Admin Epic KAN-177 S07).
 *
 * <p>조회는 클래스 기본 {@code @Transactional(readOnly = true)}, 등록/수정/삭제만 쓰기 {@code @Transactional}로
 * override — S02 {@code AdminUserService} / S04 {@code AdminShopService} 패턴 일관. CUD endpoint의 audit
 * 기록은 컨트롤러의 {@link com.chunbaetour.domain.admin.audit.LogAdminAction} AOP가 담당하며 본 서비스는
 * 도메인 상태 전이만 수행한다.
 *
 * <p>삭제는 soft delete만 지원한다 — {@link Place#delete()}로 {@code PlaceStatus.DELETED} 전이. 사용자 검색은
 * status=ACTIVE 고정 필터로 DELETED를 자동 제외한다(PlaceQueryRepository). hard delete는 본 슬라이스 범위 밖.
 *
 * <p>{@link #getTotalPlaces()}는 S10 대시보드 의존 — 관광지 카운트 쿼리는 본 서비스에만 두고 대시보드는
 * 조합만 한다. 본 슬라이스에서는 메서드 노출까지만(wiring은 S10).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPlaceService {

    private final PlaceRepository placeRepository;
    private final ShopRepository shopRepository;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 관광지 목록 검색 (keyword/category 필터 + cursor 페이징).
     *
     * <p>keyword 공백 문자열은 null로 정규화해 전체 조회로 처리(LIKE '%%' 무의미 매칭 방지). size+1 sentinel로
     * 다음 페이지 존재를 추가 쿼리 없이 판단(AdminUserService 미러). DELETED(soft delete)는 목록에서 제외.
     *
     * <p>keyword의 LIKE 와일드카드({@code % _ \})는 {@link #escapeLike}로 이스케이프해 리터럴로 처리한다 —
     * 운영자가 "50%" 같은 문자를 검색해도 의도치 않은 패턴 매칭/전체 스캔이 되지 않도록(쿼리는 ESCAPE '\' 사용).
     */
    public CursorPageResponse<AdminPlaceListResponse> getPlaces(
            String keyword, PlaceCategory category, String cursor, int size) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? escapeLike(keyword.trim()) : null;
        Long cursorId = CursorUtils.decodeSafe(cursor);

        PageRequest pageable = PageRequest.of(0, size + 1);
        List<Place> places = placeRepository.searchForAdmin(normalizedKeyword, category, cursorId, pageable);

        boolean hasNext = places.size() > size;
        List<Place> content = hasNext ? places.subList(0, size) : places;
        String nextCursor = hasNext
                ? CursorUtils.encode(content.get(content.size() - 1).getId())
                : null;

        List<AdminPlaceListResponse> responses = content.stream()
                .map(AdminPlaceListResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    /** 관광지 등록 — Place 신규 생성(ACTIVE). 등록된 상세를 반환한다. */
    @Transactional
    public AdminPlaceDetailResponse createPlace(AdminPlaceCreateRequest request) {
        Place place = placeRepository.save(request.toEntity());
        return AdminPlaceDetailResponse.from(place);
    }

    /**
     * 관광지 partial update — {@link Place#update(...)} null-skip 반영. 없으면 PLACE_NOT_FOUND(404).
     *
     * <p>이미 soft delete(DELETED)된 관광지 수정은 {@link ErrorCode#PLACE_ALREADY_DELETED}(409)로 거부한다
     * (S07 리뷰 R2 M1 — deletePlace 멱등 가드와 동일 정책). 삭제된 리소스를 수정으로 되살리는 듯한 혼란을 막는다.
     */
    @Transactional
    public AdminPlaceDetailResponse updatePlace(Long placeId, AdminPlaceUpdateRequest request) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        if (place.getStatus() == PlaceStatus.DELETED) {
            throw new BusinessException(ErrorCode.PLACE_ALREADY_DELETED);
        }
        place.update(
                request.name(),
                request.description(),
                request.address(),
                request.operatingHours(),
                request.closedDays(),
                request.phone(),
                request.admissionFee(),
                request.tags());
        // 상세 캐시(place:{id}, TTL 10분) 무효화 — 수정 내용이 최대 10분 stale로 노출되는 것 방지. 커밋 후 실행(B10).
        registerAfterCommit(() -> evictDetailCache(placeId));
        return AdminPlaceDetailResponse.from(place);
    }

    /**
     * 관광지 soft delete — {@link Place#delete()}(→DELETED). 없으면 PLACE_NOT_FOUND(404).
     *
     * <p>이미 DELETED인 관광지 재삭제는 {@link ErrorCode#PLACE_ALREADY_DELETED}(409)로 거부한다(S07 리뷰 I) —
     * 조용한 멱등 204 대신 운영자에게 "이미 삭제됨"을 명확히 알려 중복 액션/오해를 막는다.
     */
    @Transactional
    public void deletePlace(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        if (place.getStatus() == PlaceStatus.DELETED) {
            throw new BusinessException(ErrorCode.PLACE_ALREADY_DELETED);
        }
        place.delete();
        // Place는 soft delete라 FK ON DELETE SET NULL이 발화하지 않음 — 연관 shop.place_id를 서비스 레이어에서 직접 null 처리
        shopRepository.clearPlaceReference(placeId);
        // 상세 캐시(place:{id}, TTL 10분) 무효화 — 삭제된 관광지가 최대 10분 노출되는 것 방지. 커밋 후 실행(B10).
        registerAfterCommit(() -> evictDetailCache(placeId));
    }

    /**
     * DB 트랜잭션 커밋 성공 후 {@code action}을 실행하도록 등록.
     * 롤백 시 캐시만 비워지는 불일치를 막기 위해 afterCommit으로 분리. 활성 트랜잭션이 없으면(테스트 등) 즉시 실행.
     * (PlaceReviewService/PlaceLikeService의 캐시 무효화 패턴과 동일 — B10 일관화.)
     */
    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * 관광지 상세 캐시(place:{id}) 제거. 명시적 키 삭제 — REQUIRES_NEW/self-invocation 프록시 우회 함정이 없는
     * @CacheEvict 대안. 삭제 실패는 자연 TTL(10분) 만료에 위임하고 warn 로그만 남긴다(비즈니스 차단 없음).
     */
    private void evictDetailCache(Long placeId) {
        try {
            stringRedisTemplate.delete(PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + placeId);
            log.debug("관광지 상세 캐시 무효화 — placeId={}", placeId);
        } catch (Exception e) {
            log.warn("관광지 상세 캐시 무효화 실패 — placeId={}", placeId, e);
        }
    }

    // ── S10 대시보드 의존 카운트 (본 슬라이스는 메서드 노출까지) ────────────────────

    /** 전체 관광지 수 — soft delete(DELETED) 제외(S07 리뷰 H). ACTIVE/HIDDEN만 집계. */
    public long getTotalPlaces() {
        return placeRepository.countByStatusNot(PlaceStatus.DELETED);
    }

    /**
     * LIKE 와일드카드 이스케이프 — {@code \ % _}를 리터럴로 처리(ESCAPE '\' 전제).
     * 백슬래시를 가장 먼저 치환해 이중 이스케이프를 피한다.
     */
    private static String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
