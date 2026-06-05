package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.admin.market.dto.SyncResponse;
import com.chunbaetour.domain.common.config.PublicDataApiProperties;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.market.dto.response.MarketApiItem;
import com.chunbaetour.domain.market.dto.response.MarketApiResponse;
import com.chunbaetour.domain.market.service.MarketSyncBatchService.UpsertResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 전통시장 API 동기화 서비스.
 * 공공데이터포털에서 전통시장 데이터를 수집하여 DB에 저장/업데이트.
 *
 * <p>설계:
 * - HTTP fetch는 트랜잭션 밖 (네트워크 지연 방지)
 * - 배치 persist는 MarketSyncBatchService (REQUIRES_NEW 트랜잭션, 아이템 단위 격리)
 * - resultCode 검증: "00" 정상, 기타는 예외 throw
 * - upsert 키: name + address 복합 (동명 이장 대응)
 */
@Service
@Slf4j
public class MarketSyncService {

    private final RestClient restClient;
    private final PublicDataApiProperties apiProperties;
    private final LockProvider lockProvider;
    private final MarketSyncBatchService batchService;

    public MarketSyncService(
        @Qualifier("publicDataRestClient") RestClient restClient,
        PublicDataApiProperties apiProperties,
        LockProvider lockProvider,
        MarketSyncBatchService batchService
    ) {
        this.restClient = restClient;
        this.apiProperties = apiProperties;
        this.lockProvider = lockProvider;
        this.batchService = batchService;
    }

    private static final int PAGE_SIZE = 1000;

    record SyncResult(int inserted, int updated, int skipped) {
        SyncResult add(SyncResult other) {
            return new SyncResult(
                inserted + other.inserted,
                updated + other.updated,
                skipped + other.skipped
            );
        }
    }

    /**
     * 전체 전통시장 데이터 동기화 (공통 진입점).
     * 스케줄러·관리자 수동 모두 이 메서드 호출. LockProvider로 다중 인스턴스 중복 실행 차단.
     * 락 TTL: 최대 30분 (at-most-for) — 공공API 지연 + 페이지 반복 + REQUIRES_NEW 트랜잭션 누적 대응.
     * 최소 1분 (at-least-for).
     */
    public SyncResponse syncAllMarkets() {
        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(), "market_sync_all",
                Duration.ofMinutes(30), Duration.ofMinutes(1));
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig);

        if (lock.isEmpty()) {
            log.warn("[MarketSync] 락 획득 실패 — 다른 인스턴스에서 이미 수집 중 (TTL 최대 30분)");
            throw new BusinessException(ErrorCode.MARKET_SYNC_IN_PROGRESS);
        }

        try {
            return doSync();
        } finally {
            lock.get().unlock();
        }
    }

    private SyncResponse doSync() {
        log.info("[MarketSync] 전통시장 데이터 동기화 시작");
        SyncResult total = new SyncResult(0, 0, 0);

        try {
            MarketApiResponse.Response firstResponse = fetchMarketsPage(1);
            MarketApiResponse.Body firstBody = firstResponse.body();

            int totalCount = firstBody.totalCountInt();
            if (totalCount == 0) {
                log.info("[MarketSync] 동기화 대상 없음 (totalCount=0)");
                return new SyncResponse(0, 0, 0, "동기화 대상 없음");
            }
            int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
            log.info("[MarketSync] API 응답: 총 {} 건, {} 페이지", totalCount, totalPages);

            total = total.add(processBatch(firstBody.itemList()));
            log.info("[MarketSync] 페이지 1/{} 처리 완료", totalPages);

            for (int page = 2; page <= totalPages; page++) {
                MarketApiResponse.Response response = fetchMarketsPage(page);
                total = total.add(processBatch(response.body().itemList()));
                log.info("[MarketSync] 페이지 {}/{} 처리 완료", page, totalPages);
            }

            log.info("[MarketSync] 동기화 완료 — inserted={}, updated={}, skipped={}",
                    total.inserted(), total.updated(), total.skipped());
            return new SyncResponse(total.inserted(), total.updated(), total.skipped(), "전통시장 데이터 동기화 완료");
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

    SyncResult processBatch(List<MarketApiItem> items) {
        if (items == null || items.isEmpty()) {
            return new SyncResult(0, 0, 0);
        }

        int inserted = 0, updated = 0, skipped = 0;
        for (MarketApiItem item : items) {
            switch (batchService.upsertItem(item)) {
                case INSERTED -> inserted++;
                case UPDATED -> updated++;
                case SKIPPED -> skipped++;
            }
        }
        return new SyncResult(inserted, updated, skipped);
    }
}
