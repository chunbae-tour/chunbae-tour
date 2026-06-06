package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.client.TourApiPlaceDetail;
import com.chunbaetour.domain.place.client.TourApiPlaceDetailClient;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관광지 상세 온디맨드 수집(KAN-221 Tier-2).
 *
 * <p>API 수집(API_FETCH) 관광지의 상세(설명/운영시간/휴무일)가 아직 비어 있으면, 사용자 첫 상세 조회 시
 * KorService2 detailCommon2·detailIntro2를 호출해 채우고 영구 저장한다. 한 번 채워지면 이후 조회는 DB만 읽는다
 * (외부 API 일일 한도 보호).
 *
 * <p>외부 호출·저장이 실패해도 절대 상세 조회 자체를 깨뜨리지 않는다 — 실패 시 원본 Place를 그대로 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceDetailEnrichmentService {

    private final TourApiPlaceDetailClient detailClient;
    private final PlaceRepository placeRepository;

    // applyDetail(@Transactional)을 프록시 경유로 호출해 별도 트랜잭션이 실제로 적용되게 한다(self-invocation 회피).
    @Autowired @Lazy
    private PlaceDetailEnrichmentService self;

    /**
     * 필요 시 상세를 수집해 채운 Place를 반환한다. 불필요/실패 시 원본 Place를 그대로 반환.
     * HTTP 호출은 트랜잭션 밖에서 수행하고, 저장만 {@link #applyDetail}의 트랜잭션에 위임한다.
     */
    public Place enrichIfNeeded(Place place) {
        if (!needsEnrichment(place)) {
            return place;
        }
        TourApiPlaceDetail detail;
        try {
            detail = detailClient.fetchDetail(place.getExternalId());
        } catch (Exception e) {
            log.warn("관광지 상세 수집 실패 — 원본 반환: placeId={}, contentId={}, err={}",
                    place.getId(), place.getExternalId(), e.getMessage());
            return place;
        }
        try {
            return self.applyDetail(place.getId(), detail);
        } catch (Exception e) {
            log.warn("관광지 상세 저장 실패 — 원본 반환: placeId={}, err={}", place.getId(), e.getMessage());
            return place;
        }
    }

    // API 수집 관광지이면서 아직 상세 미수집(description == null)일 때만 수집 대상.
    private boolean needsEnrichment(Place place) {
        return place.getSource() == PlaceSource.API_FETCH
                && place.getExternalId() != null
                && place.getDescription() == null;
    }

    /** 상세 값을 Place에 반영하고 저장. 갱신된 엔티티 반환(상세 조회 응답·캐시에 사용). */
    @Transactional
    public Place applyDetail(Long placeId, TourApiPlaceDetail detail) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        place.applyApiDetail(detail.description(), detail.operatingHours(), detail.closedDays());
        return placeRepository.save(place);
    }
}
