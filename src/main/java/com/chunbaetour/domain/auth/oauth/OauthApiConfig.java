package com.chunbaetour.domain.auth.oauth;

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

/**
 * 소셜 로그인(카카오/네이버) OAuth 호출용 RestClient 설정.
 *
 * <p>place의 KakaoApiConfig와 동일 철학 — 외부 API 장애가 내부로 전파되지 않도록 connect/read 타임아웃을
 * 짧게 건다. 토큰 교환/사용자 조회는 로그인 경로라 빈도가 높지 않아 풀 크기는 보수적으로 둔다.
 */
@Configuration
public class OauthApiConfig {

    @Bean(destroyMethod = "close")
    public CloseableHttpClient oauthHttpClient() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(50);
        connectionManager.setDefaultMaxPerRoute(50);

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(2))
                .build();
        connectionManager.setDefaultConnectionConfig(connectionConfig);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                .setResponseTimeout(Timeout.ofSeconds(3))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Bean
    public RestClient oauthRestClient(CloseableHttpClient oauthHttpClient) {
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(oauthHttpClient);
        return RestClient.builder().requestFactory(factory).build();
    }
}
