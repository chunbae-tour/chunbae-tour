package com.chunbaetour.domain.place.client;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 한국관광공사 KorService2(국문 관광정보) 관광지 목록 수집 클라이언트 (KAN-221).
 *
 * <p>{@code areaBasedSyncList2}(관광정보 동기화 목록)를 contentTypeId=12(관광지) + 수정일순(arrange=C)으로 조회한다.
 * 응답에 {@code showflag}(삭제 플래그)와 {@code modifiedtime}이 포함되어 증분 동기화와 삭제 반영이 가능하다.
 *
 * <p>증분 수집: {@code lastModifiedTime} 이상(경계 초 포함) 변경분을 수집한다. 수정일 내림차순이므로 처음으로
 * {@code modifiedtime < lastModifiedTime}인 항목(경계보다 엄격히 과거)을 만나면 이후는 모두 이미 동기화된
 * 것이므로 즉시 중단한다. 경계와 동일 초 항목은 이전 run의 페이지 경계로 갈려 누락될 수 있어 매 run 재수집한다
 * (멱등 upsert라 안전). 최초 동기화({@code lastModifiedTime == null})는 전수 수집한다.
 */
@Slf4j
@Component
public class TourApiPlaceClient {

    private static final int    CONTENT_TYPE_TOURIST_SPOT = 12;
    private static final int    PAGE_SIZE = 100;
    private static final int    MAX_PAGES = 300; // 무한루프 방지 (전국 ~127페이지 대비 여유)
    private static final String MOBILE_OS  = "ETC";
    private static final String MOBILE_APP = "chunbae";

    private final RestClient restClient;
    private final String serviceKey;
    private final String baseUrl;

    public TourApiPlaceClient(
            @Qualifier("tourApiRestClient") RestClient restClient,
            @Value("${tour-api.kor-service.service-key}") String serviceKey,
            @Value("${tour-api.kor-service.base-url}") String baseUrl) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
    }

    /**
     * {@code lastModifiedTime}(yyyyMMddHHmmss) 이후 변경된 관광지를 페이지 단위로 수집해 {@code pageConsumer}에
     * 즉시 전달한다(KAN-262). 전 페이지를 메모리에 모은 뒤 저장하던 방식과 달리, 한 페이지를 받을 때마다 콜백을
     * 호출해 저장이 fetch와 인터리빙되도록 한다 — 첫 페이지분이 수 초 내 저장되기 시작한다.
     *
     * <p>null이면 전수 수집(최초 동기화). 수정일 내림차순이라 경계 도달 시 조기 종료한다.
     * 각 페이지 수집 시 INFO 로그를 남겨 진행 상황을 로그로 추적할 수 있다.
     */
    public void fetchModifiedSince(String lastModifiedTime, Consumer<List<TourApiPlaceItem>> pageConsumer) {
        int totalCollected = 0;
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            List<TourApiPlaceItem> items = fetchPage(pageNo).response().body().itemList();
            if (items.isEmpty()) {
                break;
            }
            List<TourApiPlaceItem> pageBatch = new ArrayList<>();
            boolean boundaryReached = false;
            for (TourApiPlaceItem item : items) {
                // 수정일 내림차순 — 경계보다 "엄격히" 과거(< )인 항목을 만나면 이후는 전부 이미 동기화됨 → 즉시 종료.
                // 경계와 동일 초(==)는 종료시키지 않는다: 같은 modifiedtime 항목이 이전 run의 페이지 경계로
                // 갈렸을 수 있어, ==에서 멈추면 다음 증분에서 영구 누락된다. 동일 초는 멱등 upsert로 재처리해도 안전.
                if (lastModifiedTime != null && item.modifiedTime() != null
                        && item.modifiedTime().compareTo(lastModifiedTime) < 0) {
                    boundaryReached = true;
                    break;
                }
                pageBatch.add(item);
            }
            if (!pageBatch.isEmpty()) {
                totalCollected += pageBatch.size();
                log.info("KorService2 페이지 수집: page={}, 건수={}, 누적={}", pageNo, pageBatch.size(), totalCollected);
                pageConsumer.accept(pageBatch); // 페이지 단위 즉시 저장 위임
            }
            if (boundaryReached) {
                log.info("KorService2 증분 수집 완료(경계 도달): collected={}", totalCollected);
                return;
            }
            if (items.size() < PAGE_SIZE) {
                break; // 가득 차지 않은 페이지 = 마지막 페이지
            }
            if (pageNo == MAX_PAGES) {
                log.error("KorService2 최대 페이지 수({}) 초과 — 무한루프 방지로 중단", MAX_PAGES);
            }
        }
        log.info("KorService2 관광지 수집 완료: collected={}", totalCollected);
    }

    private KorServiceResponse fetchPage(int pageNo) {
        String uri = buildUri(pageNo);
        try {
            KorServiceResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(KorServiceResponse.class);

            if (response == null
                    || response.response() == null
                    || response.response().header() == null
                    || response.response().body() == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            String resultCode = response.response().header().resultCode();
            if (!KorServiceResponse.SUCCESS_CODE.equals(resultCode)) {
                log.error("KorService2 error: resultCode={}, resultMsg={}",
                        resultCode, response.response().header().resultMsg());
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            return response;
        } catch (RestClientResponseException e) {
            log.error("KorService2 HTTP error: status={}", e.getStatusCode());
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        } catch (RestClientException e) {
            log.error("KorService2 network error", e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    // serviceKey는 반드시 decoded(raw) 값 + .encode() 1회 (축제 TourApiClient 패턴과 동일 — 이중 인코딩 주의).
    private String buildUri(int pageNo) {
        return UriComponentsBuilder.fromUriString(baseUrl + "/areaBasedSyncList2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", "json")
                .queryParam("numOfRows", PAGE_SIZE)
                .queryParam("pageNo", pageNo)
                .queryParam("contentTypeId", CONTENT_TYPE_TOURIST_SPOT)
                .queryParam("arrange", "C") // 수정일순(내림차순) — 증분 수집의 조기 종료 전제
                .encode()
                .build()
                .toUriString();
    }
}
