package com.chunbaetour.domain.place.client;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.dto.Coord;
import com.chunbaetour.domain.place.dto.KakaoLocalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;

@Slf4j
@Component
public class KakaoLocalApiClient {

    private final RestClient kakaoRestClient;
    private final String apiKey;
    private final String coord2AddressUrl;

    public KakaoLocalApiClient(
            RestClient kakaoRestClient,
            @Value("${kakao.map.api-key}") String apiKey,
            @Value("${kakao.map.coord2address-url}") String coord2AddressUrl
    ) {
        this.kakaoRestClient = kakaoRestClient;
        this.apiKey = apiKey;
        this.coord2AddressUrl = coord2AddressUrl;
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
                log.error("Kakao API Client Error (4xx): lng={}, lat={}, status={}, body={}", coord.lng(), coord.lat(), e.getStatusCode(), e.getResponseBodyAsString());
            } else {
                log.warn("Kakao coord2address API failed for lng={}, lat={}", coord.lng(), coord.lat(), e);
            }
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE); // PLACE_007 명세 준수
        }
    }
}
