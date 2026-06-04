package com.chunbaetour.domain.festival.client;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TourApiClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES  = 100;

    private final RestClient restClient;
    private final String serviceKey;
    private final String baseUrl;

    public TourApiClient(
            @Qualifier("tourApiRestClient") RestClient restClient,
            @Value("${tour-api.service-key}") String serviceKey,
            @Value("${tour-api.base-url}") String baseUrl) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
    }

    public List<TourApiFestivalItem> fetchAll() {
        List<TourApiFestivalItem> result = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            if (pageNo > MAX_PAGES) {
                log.error("TourAPI 최대 페이지 수({}) 초과 — 무한루프 방지로 중단", MAX_PAGES);
                break;
            }
            TourApiFestivalResponse response = fetchPage(pageNo);
            TourApiFestivalResponse.Body body = response.response().body();

            result.addAll(body.itemList());

            if (body.itemList().isEmpty() || result.size() >= body.totalCountInt()) break;
            pageNo++;
        }

        log.info("TourAPI fetchAll complete: total={}", result.size());
        return result;
    }

    private TourApiFestivalResponse fetchPage(int pageNo) {
        String uri = buildUri(pageNo);
        try {
            TourApiFestivalResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(TourApiFestivalResponse.class);

            if (response == null
                    || response.response() == null
                    || response.response().header() == null
                    || response.response().body() == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            String resultCode = response.response().header().resultCode();
            if (!"00".equals(resultCode)) {
                log.error("TourAPI error: resultCode={}, resultMsg={}",
                        resultCode, response.response().header().resultMsg());
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            return response;
        } catch (RestClientResponseException e) {
            log.error("TourAPI HTTP error: status={}", e.getStatusCode());
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        } catch (RestClientException e) {
            log.error("TourAPI network error", e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    // serviceKey는 반드시 decoded(raw) 값이어야 한다.
    // encoded key(%2B 등 포함)를 주입하면 .encode()가 %252B로 이중 인코딩하여 인증 실패가 발생한다.
    // 공공데이터포털 마이페이지에서 복사한 키를 URL decode 후 환경변수(TOUR_API_SERVICE_KEY)에 저장할 것.
    private String buildUri(int pageNo) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("serviceKey", serviceKey)
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", PAGE_SIZE)
                .queryParam("type", "json")
                .encode()
                .build()
                .toUriString();
    }
}
