package com.chunbaetour.domain.festival.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

@Configuration
public class TourApiConfig {

    @Bean(destroyMethod = "close", name = "tourApiHttpClient")
    public CloseableHttpClient tourApiHttpClient() {
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

    @SuppressWarnings({"deprecation", "removal"})
    @Bean("tourApiRestClient")
    public RestClient tourApiRestClient(CloseableHttpClient tourApiHttpClient) {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // "items": "" 를 null로 역직렬화 → Body.itemList()에서 빈 리스트 반환
                .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                // "item": {...} 단일 객체를 배열로 처리
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);

        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(tourApiHttpClient))
                .messageConverters(converters -> {
                    // Jackson 2.x/3.x 컨버터 모두 제거 후 Jackson 2.x 커스텀 컨버터를 첫 번째로 등록
                    converters.removeIf(c -> MappingJackson2HttpMessageConverter.class.isAssignableFrom(c.getClass()));
                    converters.add(0, converter);
                })
                .build();
    }
}
