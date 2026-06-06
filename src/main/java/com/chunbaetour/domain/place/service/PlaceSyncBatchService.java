package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.client.TourApiPlaceItem;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceStatus;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관광지 KorService2 수집 배치 저장 서비스 (KAN-221 Tier-1).
 *
 * <p>{@code upsertItem}은 REQUIRES_NEW 트랜잭션으로 아이템 단위 격리 — 한 건이 실패해도 전체 수집은 계속된다
 * (축제 FestivalFetchService·전통시장 MarketSyncBatchService와 동일 패턴).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceSyncBatchService {

    private final PlaceRepository placeRepository;

    public enum UpsertResult { CREATED, UPDATED, SKIPPED }

    /**
     * contentid(external_id) 기준 단일 관광지 upsert.
     * 필수 필드(contentid/title/주소/좌표) 누락이면 SKIPPED. DELETED(soft delete)는 보존하고 갱신하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UpsertResult upsertItem(TourApiPlaceItem item) {
        // 필수 필드 검증 — 좌표/이름/주소 없으면 Place 불변식 위반이므로 미리 skip
        if (isBlank(item.contentId()) || isBlank(item.title()) || isBlank(item.fullAddress())
                || isBlank(item.mapX()) || isBlank(item.mapY())) {
            return UpsertResult.SKIPPED;
        }

        BigDecimal lng = parseCoord(item.mapX());
        BigDecimal lat = parseCoord(item.mapY());
        if (lat == null || lng == null) {
            log.warn("관광지 좌표 파싱 실패 — skip: contentId={}, mapx={}, mapy={}",
                    item.contentId(), item.mapX(), item.mapY());
            return UpsertResult.SKIPPED;
        }

        String thumbnail = blankToNull(item.firstImage());
        String phone = blankToNull(item.tel());

        try {
            Optional<Place> existing = placeRepository.findByExternalId(item.contentId());
            if (existing.isEmpty()) {
                placeRepository.saveAndFlush(Place.createFromApi(
                        item.contentId(), item.title().trim(), item.fullAddress(),
                        lat, lng, thumbnail, phone));
                return UpsertResult.CREATED;
            }

            Place place = existing.get();
            // 운영자가 숨김(HIDDEN)·삭제(DELETED)한 관광지는 API 재수집으로 데이터를 덮어쓰지 않는다(운영 의사 존중).
            // ACTIVE만 갱신 — 운영자가 정상 노출 상태로 둔 것만 최신 데이터로 동기화한다.
            if (place.getStatus() != PlaceStatus.ACTIVE) {
                return UpsertResult.SKIPPED;
            }
            place.updateFromApi(item.title().trim(), item.fullAddress(), lat, lng, thumbnail, phone);
            placeRepository.saveAndFlush(place);
            return UpsertResult.UPDATED;
        } catch (DataIntegrityViolationException e) {
            // REQUIRES_NEW 트랜잭션은 이미 rollback-only — 호출부 catch에 위임
            log.warn("관광지 upsert 제약 위반 — skip: contentId={}, msg={}", item.contentId(), e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            // Place 불변식 위반(이름/주소/좌표) — 데이터 품질 문제로 skip
            log.warn("관광지 데이터 품질 오류 — skip: contentId={}, reason={}", item.contentId(), e.getMessage());
            return UpsertResult.SKIPPED;
        }
    }

    private BigDecimal parseCoord(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
