package com.chunbaetour.domain.place.client;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.dto.Coord;
import com.chunbaetour.domain.place.dto.KakaoCategoryResponse;
import com.chunbaetour.domain.place.dto.KakaoLocalResponse;
import com.chunbaetour.domain.place.dto.KakaoRegionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;

@Slf4j
@Component
public class KakaoLocalApiClient {

    private final RestClient kakaoRestClient;
    private final String apiKey;
    private final String coord2AddressUrl;
    private final String coord2RegioncodeUrl;
    private final String categorySearchUrl;

    public KakaoLocalApiClient(
            RestClient kakaoRestClient,
            @Value("${kakao.map.api-key}") String apiKey,
            @Value("${kakao.map.coord2address-url}") String coord2AddressUrl,
            @Value("${kakao.map.coord2regioncode-url}") String coord2RegioncodeUrl,
            @Value("${kakao.map.category-search-url}") String categorySearchUrl
    ) {
        this.kakaoRestClient = kakaoRestClient;
        this.apiKey = apiKey;
        this.coord2AddressUrl = coord2AddressUrl;
        this.coord2RegioncodeUrl = coord2RegioncodeUrl;
        this.categorySearchUrl = categorySearchUrl;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        org.springframework.util.Assert.hasText(apiKey, "Kakao API Key must not be blank");
    }

    public String getAddressName(Coord coord, String defaultValue) {
        try {
            String url = String.format(
                    Locale.ROOT,
                    "%s?x=%.8f&y=%.8f",
                    coord2AddressUrl, coord.lng().doubleValue(), coord.lat().doubleValue()
            );

            KakaoLocalResponse response = kakaoRestClient.get()
                    .uri(url)
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .body(KakaoLocalResponse.class);

            if (response != null && response.documents() != null && !response.documents().isEmpty()) {
                KakaoLocalResponse.Document doc = response.documents().get(0);
                if (doc.roadAddress() != null && doc.roadAddress().addressName() != null) {
                    return doc.roadAddress().addressName();
                }
                if (doc.address() != null && doc.address().addressName() != null) {
                    return doc.address().addressName();
                }
            }
            return defaultValue;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                if (e.getStatusCode().value() == 401) {
                    log.error("Kakao API Unauthorized (401): API Key might be invalid or expired.", e);
                } else {
                    // 보안: 위/경도 및 응답 Body(개인정보/키) 로깅 제거, 상태 코드만 기록
                    log.warn("Kakao API Client Error (4xx): status={}", e.getStatusCode());
                }
            } else {
                log.error("Kakao coord2address API Server Error (5xx): status={}", e.getStatusCode(), e);
            }
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE); // PLACE_007 명세 준수
        } catch (RestClientException e) {
            // [CRITICAL] 네트워크 에러(Connection Timeout, Read Timeout, DNS 등) 누수 방어
            log.error("Kakao coord2address API Network/Timeout Error", e);
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE); // PLACE_007 명세 준수
        }
    }

    public KakaoCategoryResponse searchByCategory(Coord coord, String categoryGroupCode, int radius) {
        try {
            String url = UriComponentsBuilder.fromUriString(categorySearchUrl)
                    .queryParam("category_group_code", categoryGroupCode)
                    .queryParam("x", String.format(Locale.ROOT, "%.8f", coord.lng().doubleValue()))
                    .queryParam("y", String.format(Locale.ROOT, "%.8f", coord.lat().doubleValue()))
                    .queryParam("radius", radius)
                    .queryParam("sort", "distance")
                    .build()
                    .toUriString();

            return kakaoRestClient.get()
                    .uri(url)
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .body(KakaoCategoryResponse.class);

        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                if (e.getStatusCode().value() == 401) {
                    log.error("Kakao Category API Unauthorized (401): API Key might be invalid or expired.", e);
                } else {
                    log.warn("Kakao Category API Error (4xx): status={}", e.getStatusCode());
                }
            } else {
                log.error("Kakao Category API Server Error (5xx): status={}", e.getStatusCode(), e);
            }
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        } catch (RestClientException e) {
            log.error("Kakao Category API Network Error", e);
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        }
    }

    public KakaoRegionResponse getRegionCode(double lat, double lng) {
        try {
            String url = UriComponentsBuilder.fromUriString(coord2RegioncodeUrl)
                    .queryParam("x", String.format(Locale.ROOT, "%.8f", lng))
                    .queryParam("y", String.format(Locale.ROOT, "%.8f", lat))
                    .build()
                    .toUriString();

            return kakaoRestClient.get()
                    .uri(url)
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .body(KakaoRegionResponse.class);

        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                if (e.getStatusCode().value() == 401) {
                    log.error("Kakao Region API Unauthorized (401): API Key might be invalid or expired.", e);
                } else {
                    log.warn("Kakao Region API Error (4xx): status={}", e.getStatusCode());
                }
            } else {
                log.error("Kakao Region API Server Error (5xx): status={}", e.getStatusCode(), e);
            }
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        } catch (RestClientException e) {
            log.error("Kakao Region API Network Error", e);
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        }
    }
}
