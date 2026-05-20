package com.chunbaetour.domain.payment.config;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 portone.* 설정값을 Java 객체로 바인딩하는 클래스.
 * secret, storeId, baseUrl과 결제수단별 채널키(channel)를 관리한다.
 * PortOnePaymentGatewayClient에서 주입받아 사용한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "portone")
public class PortOneProperties {

    private String secret;
    private String storeId;
    private String baseUrl;
    private Map<String, String> channel; // 결제수단별 포트원 채널키 (card, kakao-pay, toss-pay, naver-pay, foreign-card)
}
