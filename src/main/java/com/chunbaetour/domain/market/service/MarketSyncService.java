package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.common.config.PublicDataApiProperties;
import com.chunbaetour.domain.market.dto.response.MarketApiItem;
import com.chunbaetour.domain.market.dto.response.MarketApiResponse;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

/**
 * 전통시장 API 동기화 서비스.
 * 공공데이터포털에서 전통시장 데이터를 수집하여 DB에 저장/업데이트.
 */
@Service
@Slf4j
@Transactional
public class MarketSyncService {

    private final RestClient restClient;
    private final TraditionalMarketRepository marketRepository;
    private final PublicDataApiProperties apiProperties;

    public MarketSyncService(
        @Qualifier("publicDataRestClient") RestClient restClient,
        TraditionalMarketRepository marketRepository,
        PublicDataApiProperties apiProperties
    ) {
        this.restClient = restClient;
        this.marketRepository = marketRepository;
        this.apiProperties = apiProperties;
    }

    /** 한 번에 수집할 최대 행 수 (API 제한: 최대 1000) */
    private static final int PAGE_SIZE = 1000;

    /**
     * 전체 전통시장 데이터 동기화.
     * 페이징으로 전체 데이터를 수집하고 upsert 처리.
     *
     * @return 동기화된 시장 개수
     */
    public int syncAllMarkets() {
        log.info("[MarketSync] 전통시장 데이터 동기화 시작");
        int totalSynced = 0;
        int currentPage = 1;

        try {
            // 첫 페이지 요청하여 totalCount 파악
            MarketApiResponse firstResponse = fetchMarketsPage(currentPage);
            if (firstResponse == null || firstResponse.getTotalCount() == null) {
                log.warn("[MarketSync] API 응답 오류 또는 데이터 없음");
                return 0;
            }

            int totalCount = firstResponse.getTotalCount();
            int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
            log.info("[MarketSync] 총 {} 건, {} 페이지 수집 예정", totalCount, totalPages);

            // 첫 페이지 처리
            totalSynced += processBatch(firstResponse.getItems());

            // 나머지 페이지 처리
            for (int page = 2; page <= totalPages; page++) {
                MarketApiResponse response = fetchMarketsPage(page);
                if (response != null && response.getItems() != null) {
                    totalSynced += processBatch(response.getItems());
                } else {
                    log.warn("[MarketSync] 페이지 {} 수집 실패", page);
                }
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
     *
     * @param pageNo 페이지 번호 (1부터 시작)
     * @return API 응답 DTO
     */
    private MarketApiResponse fetchMarketsPage(int pageNo) {
        try {
            String url = UriComponentsBuilder.fromUriString(apiProperties.getMarket().getUrl())
                    .queryParam("serviceKey", apiProperties.getMarket().getKey())
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", PAGE_SIZE)
                    .queryParam("type", "json")
                    .toUriString();

            log.debug("[MarketSync] API 호출: pageNo={}", pageNo);
            MarketApiResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(MarketApiResponse.class);
            return response;
        } catch (Exception e) {
            log.error("[MarketSync] API 호출 실패 (pageNo={})", pageNo, e);
            return null;
        }
    }

    /**
     * 배치의 시장 항목들을 upsert 처리.
     *
     * @param items API 응답 아이템 목록
     * @return 처리된 개수
     */
    private int processBatch(List<MarketApiItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (MarketApiItem item : items) {
            try {
                // 필수 필드 검증
                if (item.getMrktNm() == null || item.getMrktNm().isBlank() ||
                    item.getRdnmadr() == null || item.getRdnmadr().isBlank() ||
                    item.getLatitude() == null || item.getLongitude() == null) {
                    log.warn("[MarketSync] 필수 필드 누락: {}", item.getMrktNm());
                    continue;
                }

                // upsert: 시장명 기준으로 중복 확인
                Optional<TraditionalMarket> existing = marketRepository.findByName(item.getMrktNm());
                if (existing.isPresent()) {
                    // 기존 데이터 업데이트
                    TraditionalMarket market = existing.get();
                    market.update(item.getMrktType(), item.getPhoneNumber(),
                                 item.getHomepageUrl(),
                                 item.getEstblYear() != null && !item.getEstblYear().isBlank()
                                     ? Integer.parseInt(item.getEstblYear())
                                     : null);
                    marketRepository.save(market);
                    log.debug("[MarketSync] 시장 정보 업데이트: {}", item.getMrktNm());
                } else {
                    // 신규 데이터 생성
                    TraditionalMarket newMarket = item.toEntity();
                    marketRepository.save(newMarket);
                    log.debug("[MarketSync] 시장 정보 신규 저장: {}", item.getMrktNm());
                }
                count++;
            } catch (Exception e) {
                log.error("[MarketSync] 시장 처리 오류: {}", item.getMrktNm(), e);
            }
        }
        return count;
    }
}
