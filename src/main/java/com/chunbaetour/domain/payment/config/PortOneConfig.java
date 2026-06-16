package com.chunbaetour.domain.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PortOneProperties.class)
public class PortOneConfig {

    // PortOne 호출 타임아웃 — 충전 직렬화 락(ChargeService) 보유 중 preRegister가 실행되므로
    // 무제한 대기 시 락이 길게 잡혀 같은 사용자 동시 요청이 PAY_008(503)로 거절된다.
    // connect/read 상한을 걸어 락 보유 시간을 강제로 제한한다 (KAN-293 리뷰 M1).
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient portOneRestClient(PortOneProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
