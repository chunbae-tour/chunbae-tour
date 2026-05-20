package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class DirectionService {

    private final String kakaoApiKey;
    private final RestClient restClient;

    public DirectionService(
            @Value("${kakao.map.api-key}") String kakaoApiKey,
            RestClient.Builder restClientBuilder
    ) {
        this.kakaoApiKey = kakaoApiKey;
        
        // [시니어 수정 1] 타임아웃 명시적 설정 (서버 스레드 풀 무한 대기 및 연쇄 장애 방지)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        
        // [시니어 수정 4] 직접 create()하지 않고 Spring Context의 Builder를 사용하여 공통 설정 상속
        this.restClient = restClientBuilder
                .requestFactory(factory)
                .build();
    }

    public String buildKakaoMapUrl(BigDecimal originLat, BigDecimal originLng, BigDecimal destLat, BigDecimal destLng) {
        String originName = getAddressName(originLng, originLat, "출발지");
        String destName = getAddressName(destLng, destLat, "도착지");

        // [시니어 수정 2] URL 인코딩 적용 (주소의 공백이나 특수문자로 인한 브라우저 파싱 에러 방지)
        String encodedOrigin = URLEncoder.encode(originName, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedDest = URLEncoder.encode(destName, StandardCharsets.UTF_8).replace("+", "%20");

        return String.format(
                Locale.ROOT,
                "https://map.kakao.com/link/from/%s,%.8f,%.8f/to/%s,%.8f,%.8f",
                encodedOrigin, originLat.doubleValue(), originLng.doubleValue(),
                encodedDest, destLat.doubleValue(), destLng.doubleValue()
        );
    }

    private String getAddressName(BigDecimal lng, BigDecimal lat, String defaultValue) {
        try {
            String url = String.format(
                    Locale.ROOT,
                    "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=%.8f&y=%.8f", 
                    lng.doubleValue(), lat.doubleValue()
            );
                    
            // [시니어 수정 3] Map 대신 내부 DTO Record를 사용하여 Type Safe하게 파싱 및 NPE 방지
            KakaoLocalResponse response = restClient.get()
                    .uri(url)
                    .header("Authorization", "KakaoAK " + kakaoApiKey)
                    .retrieve()
                    .body(KakaoLocalResponse.class);
            
            if (response != null && response.documents() != null && !response.documents().isEmpty()) {
                KakaoLocalResponse.Document doc = response.documents().get(0);
                if (doc.road_address() != null && doc.road_address().address_name() != null) {
                    return doc.road_address().address_name();
                }
                if (doc.address() != null && doc.address().address_name() != null) {
                    return doc.address().address_name();
                }
            }
            return defaultValue;
        } catch (RestClientException e) {
            log.warn("Kakao coord2address API failed for lng={}, lat={}", lng, lat, e);
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE); // PLACE_007
        }
    }

    // Type Safe 처리를 위한 내부 DTO (Jackson 3 지원)
    private record KakaoLocalResponse(List<Document> documents) {
        public record Document(RoadAddress road_address, Address address) {}
        public record RoadAddress(String address_name) {}
        public record Address(String address_name) {}
    }
}
