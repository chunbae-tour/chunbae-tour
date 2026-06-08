package com.chunbaetour.domain.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link Clock} 빈 정의.
 *
 * <p>현재 시각 의존을 인자로 받는 도메인 컴포넌트들이 테스트에서 시간을 고정/조작하기 위해 사용한다.
 * (예: {@code LogoutService}의 잔여 TTL 계산, 미래에 추가될 만료 검증 로직 등.)
 *
 * <p>운영에서는 {@link Clock#systemUTC()}를 사용해 모든 시간 비교가 UTC 기준으로 일관되게 동작하도록 한다.
 * 서버 타임존이 KST이더라도 토큰 만료/감사 로그 등은 UTC가 표준.
 *
 * <p>테스트에서는 {@code @MockBean} 또는 {@code @TestConfiguration}으로 {@link Clock#fixed}를 주입해 검증.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
