package com.chunbaetour.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 설정.
 * 외부 API 호출 시 사용 (공공데이터포털, 포트원 등).
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // TODO: RestTemplateBuilder 사용 시 timeout 설정 추가
        // .setConnectTimeout(java.time.Duration.ofSeconds(5))
        // .setReadTimeout(java.time.Duration.ofSeconds(10))
        return restTemplate;
    }
}
