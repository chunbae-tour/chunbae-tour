package com.chunbaetour.domain.place.client;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 한국관광공사 KorService2(국문 관광정보) 관광지 목록 수집 클라이언트 (KAN-221 Tier-1).
 *
 * <p>{@code areaBasedList2}를 contentTypeId=12(관광지)로 전국 전수 페이지네이션 조회한다.
 * RestClient 빈은 축제와 동일한 관용 컨버터({@code tourApiRestClient})를 재사용한다.
 *
 * <p>호출량: 전국 관광지 약 1.3만 건 ÷ {@link #PAGE_SIZE} = 약 127콜/회 → 개발계정 일일 한도(1000)
 * 안에서 1회 배치로 전수 수집 가능. 상세(설명/운영시간)는 Tier-2 온디맨드로 분리.
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

    /** 전국 관광지(contentTypeId=12) 전 페이지 수집. */
    public List<TourApiPlaceItem> fetchAll() {
        List<TourApiPlaceItem> result = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            if (pageNo > MAX_PAGES) {
                log.error("KorService2 최대 페이지 수({}) 초과 — 무한루프 방지로 중단", MAX_PAGES);
                break;
            }
            KorServiceResponse.Body body = fetchPage(pageNo).response().body();
            List<TourApiPlaceItem> items = body.itemList();
            result.addAll(items);

            if (items.isEmpty() || result.size() >= body.totalCountValue()) {
                break;
            }
            pageNo++;
        }

        log.info("KorService2 관광지 fetchAll 완료: total={}", result.size());
        return result;
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
        return UriComponentsBuilder.fromUriString(baseUrl + "/areaBasedList2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", "json")
                .queryParam("numOfRows", PAGE_SIZE)
                .queryParam("pageNo", pageNo)
                .queryParam("contentTypeId", CONTENT_TYPE_TOURIST_SPOT)
                .queryParam("arrange", "A") // 제목순 — 페이지네이션 안정 정렬
                .encode()
                .build()
                .toUriString();
    }
}
