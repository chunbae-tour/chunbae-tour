package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.util.RegionParser;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.client.TourApiPlaceItem;
import com.chunbaetour.domain.place.entity.PlaceSyncState;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.repository.PlaceSyncStateRepository;
import com.chunbaetour.domain.place.type.AreaCode;
import com.chunbaetour.domain.place.type.PlaceStatus;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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
    private final PlaceSyncStateRepository syncStateRepository;

    public enum UpsertResult { CREATED, UPDATED, SKIPPED, DELETED }

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

        // 지역(시도/시군구) 추출 — 시도는 areacode 우선(정확), 없으면 주소 파싱 fallback. 시군구는 주소 파싱.
        RegionParser.Region region = RegionParser.parse(item.fullAddress());
        String sido = AreaCode.sidoOf(item.areaCode());
        if (sido == null) {
            sido = region.sido();
        }
        String sigungu = region.sigungu();

        String modifiedTime = blankToNull(item.modifiedTime());

        try {
            Optional<Place> existing = placeRepository.findByExternalId(item.contentId());
            if (existing.isEmpty()) {
                Place created = Place.createFromApi(
                        item.contentId(), item.title().trim(), item.fullAddress(),
                        lat, lng, thumbnail, phone, sido, sigungu);
                created.syncExternalModifiedTime(modifiedTime);
                placeRepository.saveAndFlush(created);
                return UpsertResult.CREATED;
            }

            Place place = existing.get();
            // 운영자가 숨김(HIDDEN)·삭제(DELETED)한 관광지는 API 재수집으로 데이터를 덮어쓰지 않는다(운영 의사 존중).
            // ACTIVE만 갱신 — 운영자가 정상 노출 상태로 둔 것만 최신 데이터로 동기화한다.
            if (place.getStatus() != PlaceStatus.ACTIVE) {
                return UpsertResult.SKIPPED;
            }
            place.updateFromApi(item.title().trim(), item.fullAddress(), lat, lng, thumbnail, phone, sido, sigungu);
            // 외부 modifiedtime이 증가했으면 상세 재시도 한도 리셋 (뒤늦게 overview 생긴 경우 재수집 기회 회복)
            place.syncExternalModifiedTime(modifiedTime);
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
        } catch (DataAccessException e) {
            // DB/인프라 장애(락 획득 실패·쿼리 타임아웃·커넥션 풀 고갈 등) — 은닉하지 않고 전파(배치 전체 중단).
            // market #390 계약과 동일: per-item skip으로 흡수하면 DB 다운 상태로 전 item을 'skipped 성공'으로 돌게 된다.
            log.error("관광지 DB 접근 오류 — 동기화 중단: contentId={}", item.contentId(), e);
            throw e;
        }
    }

    /**
     * 동기화 응답에서 삭제(showflag != "1") 처리된 항목을 soft-delete 한다.
     * 존재하지 않거나 이미 DELETED면 SKIPPED. API 수집 데이터의 원천 삭제를 반영(MANUAL은 external_id가 null이라 매칭 안 됨).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UpsertResult markDeleted(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return UpsertResult.SKIPPED;
        }
        Optional<Place> existing = placeRepository.findByExternalId(externalId);
        if (existing.isEmpty()) {
            return UpsertResult.SKIPPED;
        }
        Place place = existing.get();
        // upsert와 동일 규칙: ACTIVE만 처리. 운영자가 숨김(HIDDEN)·삭제(DELETED)한 관광지는
        // showflag 삭제가 와도 운영 의사를 존중해 건드리지 않는다.
        if (place.getStatus() != PlaceStatus.ACTIVE) {
            return UpsertResult.SKIPPED;
        }
        place.delete();
        placeRepository.saveAndFlush(place);
        return UpsertResult.DELETED;
    }

    /**
     * 동기화 완료 후 최신 modifiedtime을 상태 테이블(단일 행)에 저장한다 — 다음 증분 수집의 경계.
     */
    @Transactional
    public void saveLastModifiedTime(String lastModifiedTime) {
        PlaceSyncState state = syncStateRepository.findById(PlaceSyncState.SINGLETON_ID)
                .orElseGet(PlaceSyncState::init);
        state.updateLastModifiedTime(lastModifiedTime);
        syncStateRepository.save(state);
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
