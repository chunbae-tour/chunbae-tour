package com.chunbaetour.domain;

import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 애플리케이션 컨텍스트가 정상적으로 부팅되는지 확인하는 sanity 테스트.
 *
 * <p>{@link AbstractIntegrationTest}를 상속하여 MySQL/Redis 컨테이너 + JWT/Cookie/CORS 테스트 properties를
 * 자동으로 받는다. 따라서 새 @ConfigurationProperties나 새 빈이 추가될 때마다 컨텍스트 로딩 실패 여부를
 * 가장 먼저 감지한다.
 */
@SpringBootTest
class ChunbaeTourApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // 빈 본문: SpringBootTest가 컨텍스트 로딩에 실패하면 이 메서드 진입 전에 예외 발생
    }
}
