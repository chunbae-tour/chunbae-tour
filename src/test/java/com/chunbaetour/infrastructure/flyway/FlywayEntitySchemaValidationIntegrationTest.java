package com.chunbaetour.infrastructure.flyway;

import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * KAN-178 (Admin Epic KAN-177 S00) entity ↔ V1__baseline.sql schema 일치 검증.
 *
 * <p>본 테스트가 검증하는 것: V1__baseline.sql이 빈 DB에 적용된 직후 Hibernate가 모든 JPA entity와
 * 실제 DB schema의 일치를 {@code ddl-auto: validate} 단계에서 검증할 때 통과하는지.
 *
 * <p>왜 별도 클래스인가:
 * <ul>
 *   <li>{@link FlywayBaselineIntegrationTest}는 raw Flyway API 검증 (Spring 컨텍스트 부팅 X)
 *       → entity validate는 Hibernate SessionFactory 필요 = Spring Boot 컨텍스트 부팅 필수</li>
 *   <li>{@link AbstractIntegrationTest}는 일반 통합 테스트용으로 Flyway 비활성 + ddl-auto: create-drop
 *       → entity 기반 schema 자동 생성하므로 V1과 entity 일치 여부를 측정할 수 없음</li>
 * </ul>
 *
 * <p>검증 방식: {@code AbstractIntegrationTest}를 상속해 testcontainers MySQL + Redis 컨테이너와
 * 운영 설정을 모두 재사용하되, {@code spring.flyway.enabled=true} + {@code ddl-auto: validate}만
 * override해서 운영 prod 부팅 흐름을 모사한다. Spring Boot 컨텍스트가 정상 부팅되면 다음 두 단계가
 * 모두 성공했다는 의미:
 * <ol>
 *   <li>Flyway가 빈 testcontainer MySQL에 V1__baseline.sql을 실제 실행해 schema 생성
 *       (testcontainer는 비어 있으므로 baseline-on-migrate=true가 발동하지 않고 V1 일반 실행)</li>
 *   <li>Hibernate가 ddl-auto: validate로 모든 entity 매핑이 V1 schema와 일치함을 검증</li>
 * </ol>
 *
 * <p>본 테스트 실패가 의미하는 것: V1__baseline.sql의 컬럼명/타입/nullable/길이가 entity와 불일치.
 * 운영 prod 배포 시 같은 검증이 부팅 단계에서 실패해 앱이 뜨지 않는 사고를 미리 차단한다.
 *
 * <p>테스트 메서드는 의도적으로 비어 있다. 컨텍스트 부팅 자체가 검증이므로 별도 assertion 불필요 —
 * 부팅 성공 = 검증 통과, 부팅 실패 = 검증 실패.
 */
@SpringBootTest
class FlywayEntitySchemaValidationIntegrationTest extends AbstractIntegrationTest {

    /**
     * 부모 {@link AbstractIntegrationTest}가 등록한
     * {@code spring.flyway.enabled=false} / {@code spring.jpa.hibernate.ddl-auto=create-drop}을
     * 본 테스트용으로 override.
     *
     * <p>Spring Test의 {@code @DynamicPropertySource}는 부모/자식 모두 적용되며 자식이 나중에
     * 등록한 값이 우선된다. 따라서 본 클래스 전용 컨텍스트만 Flyway 활성 + validate로 부팅된다.
     */
    @DynamicPropertySource
    static void overrideForFlywayValidate(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    @DisplayName("V1__baseline.sql 적용 후 모든 entity가 Hibernate validate를 통과 (컨텍스트 부팅 자체가 검증)")
    void contextLoads_meansV1SchemaMatchesEntities() {
        // 컨텍스트 부팅이 완료되었다는 것 자체가 검증 — Flyway V1 실행 + Hibernate validate 통과.
        // 본 메서드 도달 = 운영 prod 첫 배포 시 ddl-auto: validate 단계 통과 보장.
    }
}
