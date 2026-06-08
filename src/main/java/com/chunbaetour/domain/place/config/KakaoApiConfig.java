package com.chunbaetour.domain.place.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class KakaoApiConfig {

    @Bean(destroyMethod = "close")
    public CloseableHttpClient kakaoHttpClient() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(100); // [CRITICAL] 카카오 단일 호스트 전용이므로 MaxTotal과 동일해야 100개 풀을 온전히 씀

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(1)) // [CRITICAL] 외부 API 장애가 내부로 전파되는 것 방지 (1초)
                .build();
        connectionManager.setDefaultConnectionConfig(connectionConfig);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(1)) // [CRITICAL] 풀에서 커넥션을 빌려올 때 무한 대기 방지
                .setResponseTimeout(Timeout.ofSeconds(2)) // [CRITICAL] Read Timeout 2초 설정
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Bean
    public RestClient kakaoRestClient(CloseableHttpClient kakaoHttpClient) {
        // Spring Boot 4에서 RestClient.Builder 자동 구성이 일부 환경(통합 테스트 등)에서 누락됨.
        // RestClient.builder()를 직접 호출하여 컨텍스트 부팅 실패 회피 (KAN-65 Part 2/2 머지 시 develop broken 복구).
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(kakaoHttpClient);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean(destroyMethod = "close")
    public ExecutorService kakaoVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
