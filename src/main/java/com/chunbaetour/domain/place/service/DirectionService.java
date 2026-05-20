package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PreDestroy;
import java.io.IOException;

@Slf4j
@Service
public class DirectionService {

    private final String kakaoApiKey;
    private final RestClient restClient;
    private final CloseableHttpClient httpClient;
    // [CRITICAL] Java 21 Virtual Thread 적용 (Network I/O 블로킹으로 인한 스레드 풀 고갈 방지)
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public DirectionService(
            @Value("${kakao.map.api-key:}") String kakaoApiKey,
            RestClient.Builder restClientBuilder
    ) {
        this.kakaoApiKey = kakaoApiKey;
        
        // [HIGH] 커넥션 풀 적용 (Apache HttpClient 5)
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(20);
        
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(2))
                .build();
        connectionManager.setDefaultConnectionConfig(connectionConfig);

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofSeconds(3))
                .build();

        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(this.httpClient);
        
        this.restClient = restClientBuilder
                .requestFactory(factory)
                .build();
    }

    // [CRITICAL] 서버 종료 시 커넥션 풀 및 스레드 풀 자원 누수(Memory/Socket Leak) 방지를 위한 우아한 종료
    @PreDestroy
    public void closeResources() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            log.warn("Error closing HttpClient", e);
        }
        if (virtualThreadExecutor != null) {
            virtualThreadExecutor.close();
        }
    }

    // [MEDIUM] 파라미터 순서 뒤바뀜 방지를 위한 전용 타입
    public record Coord(BigDecimal lat, BigDecimal lng) {}

    public String buildKakaoMapUrl(Coord origin, Coord dest) {
        // [MEDIUM -> CRITICAL 수정] 기본 ForkJoinPool 대신 Virtual Thread Executor 사용
        CompletableFuture<String> originFuture = CompletableFuture.supplyAsync(() -> getAddressName(origin, "출발지"), virtualThreadExecutor);
        CompletableFuture<String> destFuture = CompletableFuture.supplyAsync(() -> getAddressName(dest, "도착지"), virtualThreadExecutor);

        CompletableFuture.allOf(originFuture, destFuture).join();
        
        String originName = originFuture.join();
        String destName = destFuture.join();

        String encodedOrigin = URLEncoder.encode(originName, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedDest = URLEncoder.encode(destName, StandardCharsets.UTF_8).replace("+", "%20");

        return String.format(
                Locale.ROOT,
                "https://map.kakao.com/link/from/%s,%.8f,%.8f/to/%s,%.8f,%.8f",
                encodedOrigin, origin.lat().doubleValue(), origin.lng().doubleValue(),
                encodedDest, dest.lat().doubleValue(), dest.lng().doubleValue()
        );
    }

    private String getAddressName(Coord coord, String defaultValue) {
        try {
            String url = String.format(
                    Locale.ROOT,
                    "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=%.8f&y=%.8f", 
                    coord.lng().doubleValue(), coord.lat().doubleValue()
            );
                    
            KakaoLocalResponse response = restClient.get()
                    .uri(url)
                    .header("Authorization", "KakaoAK " + kakaoApiKey)
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
        } catch (HttpClientErrorException e) {
            // [HIGH] 4xx 클라이언트 에러 (예: 키 설정 오류 등) 분리 및 명확한 로그 남김
            log.error("Kakao API Client Error (4xx): lng={}, lat={}, status={}, body={}", coord.lng(), coord.lat(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE); // PLACE_007 명세 준수
        } catch (RestClientException e) {
            log.warn("Kakao coord2address API failed for lng={}, lat={}", coord.lng(), coord.lat(), e);
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE); // PLACE_007 명세 준수
        }
    }

    private record KakaoLocalResponse(List<Document> documents) {
        public record Document(
                @JsonProperty("road_address") RoadAddress roadAddress,
                Address address
        ) {}
        public record RoadAddress(@JsonProperty("address_name") String addressName) {}
        public record Address(@JsonProperty("address_name") String addressName) {}
    }
}
