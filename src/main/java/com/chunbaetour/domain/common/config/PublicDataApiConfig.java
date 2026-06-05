package com.chunbaetour.domain.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * 공공데이터포털 API 통합 설정.
 * 전통시장 API, 관광지 API 등을 지원.
 */
@Configuration
@EnableConfigurationProperties(PublicDataApiProperties.class)
public class PublicDataApiConfig {

    @Bean(destroyMethod = "close", name = "publicDataHttpClient")
    public HttpClient publicDataHttpClient() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(10);
        cm.setDefaultMaxPerRoute(10);
        cm.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(5))
                .build());

        return HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                        .setResponseTimeout(Timeout.ofSeconds(10))
                        .build())
                .build();
    }

    @Bean("publicDataRestClient")
    public RestClient publicDataRestClient(HttpClient publicDataHttpClient) {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // 빈 문자열을 null로 역직렬화
                .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                // 단일 객체를 배열로 처리
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);

        return RestClient.builder()
                .requestFactory(new org.springframework.http.client.HttpComponentsClientHttpRequestFactory(publicDataHttpClient))
                .messageConverters(converters -> {
                    converters.removeIf(c -> MappingJackson2HttpMessageConverter.class.isAssignableFrom(c.getClass()));
                    converters.add(0, converter);
                })
                .build();
    }
}
