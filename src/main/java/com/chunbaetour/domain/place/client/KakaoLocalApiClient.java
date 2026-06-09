package com.chunbaetour.domain.place.client;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.dto.Coord;
import com.chunbaetour.domain.place.dto.KakaoAddressResponse;
import com.chunbaetour.domain.place.dto.KakaoCategoryResponse;
import com.chunbaetour.domain.place.dto.KakaoLocalResponse;
import com.chunbaetour.domain.place.dto.KakaoRegionResponse;
import com.chunbaetour.domain.place.dto.KakaoKeywordResponse;
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
    private final String addressSearchUrl;
    private final String keywordSearchUrl;

    public KakaoLocalApiClient(
            RestClient kakaoRestClient,
            @Value("${kakao.map.api-key}") String apiKey,
            @Value("${kakao.map.coord2address-url}") String coord2AddressUrl,
            @Value("${kakao.map.coord2regioncode-url}") String coord2RegioncodeUrl,
            @Value("${kakao.map.category-search-url}") String categorySearchUrl,
            @Value("${kakao.map.address-search-url}") String addressSearchUrl,
            @Value("${kakao.map.keyword-search-url}") String keywordSearchUrl
    ) {
        this.kakaoRestClient = kakaoRestClient;
        this.apiKey = apiKey;
        this.coord2AddressUrl = coord2AddressUrl;
        this.coord2RegioncodeUrl = coord2RegioncodeUrl;
        this.categorySearchUrl = categorySearchUrl;
        this.addressSearchUrl = addressSearchUrl;
        this.keywordSearchUrl = keywordSearchUrl;
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
                    log.error("Kakao API Unauthorized (401): API Key might be invalid or expired. error={}", e.getMessage(), e);
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
                    log.error("Kakao Category API Unauthorized (401): API Key might be invalid or expired. error={}", e.getMessage(), e);
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

    /**
     * 주소 → 좌표 변환 (Geocoding).
     * • 도로명/지번 주소 모두 지원
     * • 일치 결과가 없으면 documents가 빈 리스트인 KakaoAddressResponse를 반환 (null 아님)
     * • 카카오 API 장애 시 MAP_SERVICE_UNAVAILABLE 예외 발생
     */
    public KakaoAddressResponse searchAddress(String query) {
        try {
            String url = UriComponentsBuilder.fromUriString(addressSearchUrl)
                    .queryParam("query", query)
                    .queryParam("size", 1)
                    .build()
                    .toUriString();

            return kakaoRestClient.get()
                    .uri(url)
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .body(KakaoAddressResponse.class);

        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                if (e.getStatusCode().value() == 401) {
                    log.error("Kakao Address Search API Unauthorized (401): API Key might be invalid or expired. error={}", e.getMessage(), e);
                } else {
                    log.warn("Kakao Address Search API Error (4xx): status={}", e.getStatusCode());
                }
            } else {
                log.error("Kakao Address Search API Server Error (5xx): status={}", e.getStatusCode(), e);
            }
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        } catch (RestClientException e) {
            log.error("Kakao Address Search API Network Error", e);
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
                    log.error("Kakao Region API Unauthorized (401): API Key might be invalid or expired. error={}", e.getMessage(), e);
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

    /**
     * 카카오 로컬 API - 키워드로 장소 검색.
     * <p>자동완성 등에서 사용되며, API 호출 실패 시 MAP_SERVICE_UNAVAILABLE 예외를 발생시켜 호출자가 장애 상태를 감지하고 처리할 수 있도록 한다.</p>
     * @throws com.chunbaetour.domain.common.error.BusinessException API 호출 실패(4xx/5xx) 또는 네트워크 오류 시
     */
    public KakaoKeywordResponse searchByKeyword(String query, int size) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        int normalizedSize = Math.max(1, Math.min(size, 15));

        try {
            String url = UriComponentsBuilder.fromUriString(keywordSearchUrl)
                    .queryParam("query", query)
                    .queryParam("size", normalizedSize)
                    .encode()
                    .build()
                    .toUriString();

            return kakaoRestClient.get()
                    .uri(url)
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .body(KakaoKeywordResponse.class);

        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                if (e.getStatusCode().value() == 401) {
                    log.error("Kakao Keyword API Unauthorized (401): API Key might be invalid or expired.", e);
                } else {
                    log.warn("Kakao Keyword API Error (4xx): status={}", e.getStatusCode());
                }
            } else {
                log.error("Kakao Keyword API Server Error (5xx): status={}", e.getStatusCode().value(), e);
            }
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        } catch (RestClientException e) {
            log.error("Kakao Keyword API Network Error", e);
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        }
    }
}
