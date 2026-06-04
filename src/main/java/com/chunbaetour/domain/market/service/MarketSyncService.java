package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.common.config.PublicDataApiProperties;
import com.chunbaetour.domain.market.dto.response.MarketApiItem;
import com.chunbaetour.domain.market.dto.response.MarketApiResponse;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 전통시장 API 동기화 서비스.
 * 공공데이터포털에서 전통시장 데이터를 수집하여 DB에 저장/업데이트.
 *
 * <p>설계:
 * - HTTP fetch는 트랜잭션 밖 (네트워크 지연 방지)
 * - 배치 persist는 별도 트랜잭션 (per-batch 격리)
 * - resultCode 검증: "00" 정상, 기타는 예외 throw
 * - upsert 키: name + address 복합 (동명 이장 대응)
 */
@Service
@Slf4j
public class MarketSyncService {

    private final RestClient restClient;
    private final TraditionalMarketRepository marketRepository;
    private final PublicDataApiProperties apiProperties;
    private final LockProvider lockProvider;

    @Autowired @Lazy private MarketSyncService self;

    public MarketSyncService(
        @Qualifier("publicDataRestClient") RestClient restClient,
        TraditionalMarketRepository marketRepository,
        PublicDataApiProperties apiProperties,
        LockProvider lockProvider
    ) {
        this.restClient = restClient;
        this.marketRepository = marketRepository;
        this.apiProperties = apiProperties;
        this.lockProvider = lockProvider;
    }

    /** 한 번에 수집할 최대 행 수 (API 제한: 최대 1000) */
    private static final int PAGE_SIZE = 1000;

    /**
     * 전체 전통시장 데이터 동기화 (공통 진입점).
     * 스케줄러·관리자 수동 모두 이 메서드 호출. LockProvider로 다중 인스턴스 중복 실행 차단.
     * 페이징으로 전체 데이터 수집 후 아이템 단위 REQUIRES_NEW upsert.
     *
     * @return 동기화된 시장 개수 (락 획득 실패 시 0)
     */
    public int syncAllMarkets() {
        // 분산 락 획득: 스케줄러·admin 공통 진입점 → 한 인스턴스만 실행
        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(), "market_sync_all",
                Duration.ofMinutes(10), Duration.ofMinutes(1));
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig);

        if (lock.isEmpty()) {
            log.warn("[MarketSync] 락 획득 실패 — 다른 인스턴스에서 이미 수집 중");
            throw new BusinessException(ErrorCode.MARKET_SYNC_IN_PROGRESS);
        }

        try {
            return doSync();
        } finally {
            lock.get().unlock();
        }
    }

    private int doSync() {
        log.info("[MarketSync] 전통시장 데이터 동기화 시작");
        int totalSynced = 0;
        int currentPage = 1;

        // HTTP fetch는 TX 밖 (다중 순차 호출로 인한 커넥션 장시간 점유 방지)
        try {
            // 첫 페이지 요청하여 totalCount 파악
            MarketApiResponse.Response firstResponse = fetchMarketsPage(currentPage);
            MarketApiResponse.Body firstBody = firstResponse.body();

            int totalCount = firstBody.totalCountInt();
            int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
            log.info("[MarketSync] 총 {} 건, {} 페이지 수집 예정", totalCount, totalPages);

            // 첫 페이지 처리 (아이템 단위 REQUIRES_NEW TX)
            totalSynced += processBatch(firstBody.itemList());

            // 나머지 페이지 처리 (각 배치마다 독립 TX)
            for (int page = 2; page <= totalPages; page++) {
                MarketApiResponse.Response response = fetchMarketsPage(page);
                totalSynced += processBatch(response.body().itemList());
            }

            log.info("[MarketSync] 동기화 완료: {} 건", totalSynced);
            return totalSynced;
        } catch (Exception e) {
            log.error("[MarketSync] 동기화 중 오류 발생", e);
            throw new RuntimeException("전통시장 데이터 동기화 실패", e);
        }
    }

    /**
     * API에서 지정된 페이지의 시장 데이터 조회.
     * resultCode 검증: "00" 정상, 기타는 예외 throw.
     *
     * @param pageNo 페이지 번호 (1부터 시작)
     * @return API 응답 Response (header + body)
     * @throws IllegalStateException resultCode != "00"
     */
    private MarketApiResponse.Response fetchMarketsPage(int pageNo) {
        try {
            // serviceKey는 반드시 decoded(raw) 값 + .encode() 1회 (festival TourApiClient 패턴)
            String url = UriComponentsBuilder.fromUriString(apiProperties.getMarket().getUrl())
                    .queryParam("serviceKey", apiProperties.getMarket().getKey())
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", PAGE_SIZE)
                    .queryParam("type", "json")
                    .encode()
                    .toUriString();

            log.debug("[MarketSync] API 호출: pageNo={}", pageNo);
            MarketApiResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(MarketApiResponse.class);

            if (response == null || response.response() == null) {
                throw new IllegalStateException("[MarketSync] API 응답 구조 오류 (pageNo=" + pageNo + ")");
            }

            MarketApiResponse.Response apiResponse = response.response();
            MarketApiResponse.Header header = apiResponse.header();

            if (header == null) {
                throw new IllegalStateException("[MarketSync] API header 누락 (pageNo=" + pageNo + ")");
            }

            String resultCode = header.resultCode();
            String resultMsg = header.resultMsg();

            // resultCode 검증: "03" (데이터 없음)은 로그만, 기타는 예외
            if (!"00".equals(resultCode)) {
                if ("03".equals(resultCode)) {
                    log.debug("[MarketSync] 데이터 없음 (pageNo={}): {}", pageNo, resultMsg);
                    // 빈 body 반환 (페이지네이션 종료)
                    return new MarketApiResponse.Response(
                        new MarketApiResponse.Header(resultCode, resultMsg),
                        new MarketApiResponse.Body(List.of(), "0", String.valueOf(pageNo), "0")
                    );
                }
                handleApiError(resultCode, resultMsg, pageNo);
            }

            MarketApiResponse.Body body = apiResponse.body();
            if (body == null) {
                throw new IllegalStateException("[MarketSync] API body 누락 (pageNo=" + pageNo + ")");
            }

            return apiResponse;
        } catch (IllegalStateException e) {
            throw e; // resultCode 검증 오류는 재throw
        } catch (Exception e) {
            log.error("[MarketSync] API 호출 실패 (pageNo={})", pageNo, e);
            throw new IllegalStateException("[MarketSync] API 호출 실패 (pageNo=" + pageNo + ")", e);
        }
    }

    /**
     * 공공데이터포털 에러코드별 처리.
     * 에러코드에 따라 로깅 수준을 다르게 하되, 모두 예외 throw.
     */
    private void handleApiError(String resultCode, String resultMsg, int pageNo) {
        String errorMessage = String.format(
            "[MarketSync] API 에러 (pageNo=%d), resultCode=%s: %s",
            pageNo, resultCode, resultMsg
        );

        switch (resultCode) {
            case "03" -> {
                // "03"은 위에서 이미 처리되므로 여기 도달하지 않음
                log.debug("{} (데이터 없음 — 이미 처리됨)", errorMessage);
                return;
            }
            case "05", "22" -> {
                log.warn("{} (일시적 오류 — 재시도 권장)", errorMessage);
                throw new IllegalStateException(errorMessage);
            }
            case "20", "21", "30", "31", "32" -> {
                log.error("{} (설정 오류 — 관리자 확인 필요)", errorMessage);
                throw new IllegalStateException(errorMessage);
            }
            default -> {
                log.error("{} (기타 에러)", errorMessage);
                throw new IllegalStateException(errorMessage);
            }
        }
    }

    /**
     * 배치의 시장 항목들을 순회하며 upsert.
     * 각 아이템은 upsertItem()으로 독립 TX (REQUIRES_NEW) 격리 → 1건 위반이 그 1건만 롤백.
     *
     * @param items API 응답 아이템 목록
     * @return 성공한 개수
     */
    public int processBatch(List<MarketApiItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (MarketApiItem item : items) {
            if (self.upsertItem(item)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 단일 아이템 upsert (아이템 단위 독립 TX).
     * REQUIRES_NEW: 호출 TX와 무관하게 새 TX 시작 → 예외 시 해당 아이템만 롤백.
     *
     * @param item API 응답 아이템
     * @return 성공 여부
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean upsertItem(MarketApiItem item) {
        try {
            // 필수 필드 검증
            if (item.getMrktNm() == null || item.getMrktNm().isBlank() ||
                item.getRdnmadr() == null || item.getRdnmadr().isBlank() ||
                item.getLatitude() == null || item.getLongitude() == null) {
                log.warn("[MarketSync] 필수 필드 누락: {}", item.getMrktNm());
                return false;
            }

            // upsert: name + address 복합 키
            Optional<TraditionalMarket> existing = marketRepository.findByNameAndAddress(
                item.getMrktNm(), item.getRdnmadr()
            );

            if (existing.isPresent()) {
                // 업데이트
                TraditionalMarket market = existing.get();
                market.update(
                    item.getMrktType(),
                    item.getPhoneNumber(),
                    item.getHomepageUrl(),
                    item.getEstblYear() != null && !item.getEstblYear().isBlank()
                        ? Integer.parseInt(item.getEstblYear())
                        : null
                );
                marketRepository.save(market);
                log.debug("[MarketSync] 시장 정보 업데이트: {} ({})", item.getMrktNm(), item.getRdnmadr());
            } else {
                // 신규
                TraditionalMarket newMarket = item.toEntity();
                marketRepository.save(newMarket);
                log.debug("[MarketSync] 시장 정보 신규 저장: {} ({})", item.getMrktNm(), item.getRdnmadr());
            }
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("[MarketSync] DB 제약 위반 (시장: {}): {}", item.getMrktNm(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("[MarketSync] 시장 처리 오류: {}", item.getMrktNm(), e);
            return false;
        }
    }
}
