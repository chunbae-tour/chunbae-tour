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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TourApiClient {

    private static final String OPERATION = "/searchFestival2";
    private static final int PAGE_SIZE = 100;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;
    private final String serviceKey;
    private final String baseUrl;

    public TourApiClient(
            @Qualifier("tourApiRestClient") RestClient restClient,
            @Value("${tour-api.service-key}") String serviceKey,
            @Value("${tour-api.base-url}") String baseUrl) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public List<TourApiFestivalItem> fetchAll() {
        String startDate = LocalDate.now().minusYears(1).format(DATE_FMT);
        String endDate   = LocalDate.now().plusYears(2).format(DATE_FMT);

        List<TourApiFestivalItem> result = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            TourApiFestivalResponse response = fetchPage(startDate, endDate, pageNo);
            TourApiFestivalResponse.Body body = response.response().body();

            result.addAll(body.itemList());

            if (body.itemList().isEmpty() || result.size() >= body.totalCount()) break;
            pageNo++;
        }

        log.info("TourAPI fetchAll complete: total={}", result.size());
        return result;
    }

    private TourApiFestivalResponse fetchPage(String startDate, String endDate, int pageNo) {
        String uri = buildUri(startDate, endDate, pageNo);
        try {
            TourApiFestivalResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(TourApiFestivalResponse.class);

            if (response == null || response.response() == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            String resultCode = response.response().header().resultCode();
            if (!"0000".equals(resultCode)) {
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

    private String buildUri(String startDate, String endDate, int pageNo) {
        return UriComponentsBuilder.fromUriString(baseUrl + OPERATION)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "ChunbaeTour")
                .queryParam("_type", "json")
                .queryParam("eventStartDate", startDate)
                .queryParam("eventEndDate", endDate)
                .queryParam("numOfRows", PAGE_SIZE)
                .queryParam("pageNo", pageNo)
                .queryParam("arrange", "C")
                .encode()
                .build()
                .toUriString();
    }
}
