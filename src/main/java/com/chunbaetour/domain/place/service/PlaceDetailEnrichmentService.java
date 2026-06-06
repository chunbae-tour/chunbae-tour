package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.client.TourApiPlaceDetail;
import com.chunbaetour.domain.place.client.TourApiPlaceDetailClient;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceSource;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private static final Duration ENRICH_LOCK_TTL = Duration.ofSeconds(10);

    private final TourApiPlaceDetailClient detailClient;
    private final PlaceRepository placeRepository;
    private final StringRedisTemplate redisTemplate;

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

        // 동시 최초 조회 레이스 방지: contentid 단위 짧은 분산 락. 미획득이면 다른 요청이 수집 중이므로
        // 이번엔 원본 반환(외부 API 중복 호출·중복 write 차단). 다음 조회 때 채워진 값을 캐시/DB에서 본다.
        // Redis 장애 시에는 락 없이 진행(상세 채우기 우선) — 중복 호출만 감수, applyDetail 가드가 중복 write를 막는다.
        String lockKey = "lock:place:enrich:" + place.getExternalId();
        boolean acquired;
        try {
            acquired = Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(lockKey, "1", ENRICH_LOCK_TTL));
        } catch (Exception e) {
            acquired = true; // Redis 장애 → 락 우회 진행
            lockKey = null;
        }
        if (!acquired) {
            return place;
        }

        try {
            TourApiPlaceDetail detail = detailClient.fetchDetail(place.getExternalId());
            return self.applyDetail(place.getId(), detail);
        } catch (Exception e) {
            log.warn("관광지 상세 수집/저장 실패 — 원본 반환: placeId={}, contentId={}, err={}",
                    place.getId(), place.getExternalId(), e.getMessage());
            return place;
        } finally {
            if (lockKey != null) {
                try {
                    redisTemplate.delete(lockKey);
                } catch (Exception ignore) {
                    // 락 해제 실패는 TTL(10초)로 자동 만료되므로 무시
                }
            }
        }
    }

    // API 수집 관광지이면서 아직 상세 미수집(description == null)일 때만 수집 대상.
    private boolean needsEnrichment(Place place) {
        return place.getSource() == PlaceSource.API_FETCH
                && place.getExternalId() != null
                && place.getDescription() == null;
    }

    /**
     * 상세 값을 Place에 반영하고 저장. 갱신된 엔티티 반환(상세 조회 응답·캐시에 사용).
     *
     * <p>@Transactional self-proxy 적용을 위해 public이어야 한다(Spring은 public 메서드만 프록시). 가드 우회
     * (다른 빈의 직접 호출)·동시 재진입에 대비해, 이미 상세가 채워진 경우(description != null) 재적용 없이 반환한다.
     */
    @Transactional
    public Place applyDetail(Long placeId, TourApiPlaceDetail detail) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        if (place.getDescription() != null) {
            return place; // 이미 수집됨(다른 요청이 선반영) — 중복 write 방지(idempotent)
        }
        place.applyApiDetail(detail.description(), detail.operatingHours(), detail.closedDays());
        return placeRepository.save(place);
    }
}
