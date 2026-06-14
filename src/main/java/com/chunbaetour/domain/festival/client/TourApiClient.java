package com.chunbaetour.domain.festival.client;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatusCode;
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
    private final int maxAttempts;
    private final long initialBackoffMillis;

    public TourApiClient(
            @Qualifier("tourApiRestClient") RestClient restClient,
            @Value("${tour-api.service-key}") String serviceKey,
            @Value("${tour-api.base-url}") String baseUrl,
            @Value("${tour-api.retry.max-attempts}") int maxAttempts,
            @Value("${tour-api.retry.initial-backoff-millis}") long initialBackoffMillis) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
        this.maxAttempts = maxAttempts;
        this.initialBackoffMillis = initialBackoffMillis;
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

    // 일시적 장애(네트워크 단절, 5xx)는 지수 백오프로 재시도한다.
    // 비일시적(4xx, resultCode != "00", 응답 형식 오류)은 재시도해도 동일 실패라 즉시 중단한다.
    private TourApiFestivalResponse fetchPage(int pageNo) {
        String uri = buildUri(pageNo);
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return requestAndValidate(uri);
            } catch (RestClientResponseException e) {
                // HTTP 상태 오류 — 5xx만 재시도, 4xx는 즉시 실패
                if (isRetryable(e.getStatusCode()) && attempt < maxAttempts) {
                    log.warn("TourAPI 5xx (status={}), 재시도 {}/{}", e.getStatusCode(), attempt, maxAttempts);
                    backoff(attempt);
                    continue;
                }
                log.error("TourAPI HTTP error: status={}, attempt={}", e.getStatusCode(), attempt);
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            } catch (RestClientException e) {
                // 네트워크 오류(응답 없음) — 재시도 대상
                if (attempt < maxAttempts) {
                    log.warn("TourAPI 네트워크 오류, 재시도 {}/{}: {}", attempt, maxAttempts, e.getMessage());
                    backoff(attempt);
                    continue;
                }
                log.error("TourAPI network error after {} attempts", attempt, e);
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }
        }
    }

    // HTTP 호출 + 응답 유효성 검증. 형식 오류·resultCode 오류는 비일시적이므로 BusinessException으로
    // 던져 재시도 루프를 벗어난다(RestClient 예외가 아니라 catch되지 않음).
    private TourApiFestivalResponse requestAndValidate(String uri) {
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
    }

    private boolean isRetryable(HttpStatusCode status) {
        return status.is5xxServerError();
    }

    // 지수 백오프: initial * 2^(attempt-1) → 500, 1000, 2000ms ...
    private void backoff(int attempt) {
        long delay = initialBackoffMillis * (1L << (attempt - 1));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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
