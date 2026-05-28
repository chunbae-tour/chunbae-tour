package com.chunbaetour.domain.translation.config;

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

@Configuration
public class GoogleTranslationConfig {

    // Translation API 전용 HttpClient 빈 생성 — 커넥션 풀 + 타임아웃 설정 포함
    @Bean(destroyMethod = "close")
    public CloseableHttpClient googleTranslationHttpClient() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(50);
        connectionManager.setDefaultMaxPerRoute(50);

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(3))
                .build();
        connectionManager.setDefaultConnectionConfig(connectionConfig);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(3))
                .setResponseTimeout(Timeout.ofSeconds(5))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    // Translation API 전용 RestClient 빈 생성 — googleTranslationHttpClient를 request factory로 사용
    @Bean
    public RestClient googleTranslationRestClient(CloseableHttpClient googleTranslationHttpClient) {
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(googleTranslationHttpClient);
        return RestClient.builder().requestFactory(factory).build();
    }
}
