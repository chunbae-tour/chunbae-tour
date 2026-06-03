package com.chunbaetour.infrastructure.flyway;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * KAN-178 (Admin Epic KAN-177 S00) Flyway 도입 검증.
 *
 * <p>본 테스트가 검증하는 외부 동작 두 가지:
 * <ol>
 *   <li><b>빈 DB 시나리오</b> (테스트 환경): Flyway가 V1__baseline.sql을 실제 실행해 schema 생성 +
 *       flyway_schema_history에 V1 row(success=true) 기록. 검증 방법은 baseline-on-migrate=false +
 *       baseline-version=0으로 baseline 메커니즘을 비활성해 V1을 일반 마이그레이션으로 실행하는 것.</li>
 *   <li><b>운영 prod baseline 시나리오</b>: 기존 schema가 있는 DB에서 baseline-on-migrate=true +
 *       baseline-version=1로 Flyway 부팅 시 V1을 BASELINE marker로 기록만 하고 schema 변경 0인 것.
 *       실제 운영 응용 케이스의 시뮬레이션.</li>
 * </ol>
 *
 * <p>본 테스트는 {@link com.chunbaetour.domain.support.AbstractIntegrationTest}를 상속하지 않는다 —
 * 일반 통합 테스트는 Flyway 비활성 + Hibernate ddl-auto: create-drop으로 동작하므로, Flyway 자체 동작
 * 검증은 본 테스트가 자체 testcontainers 컨테이너를 띄워 격리해야 한다.
 *
 * <p>본 테스트 실패가 의미하는 것:
 * <ul>
 *   <li>V1 적용 후 ad_applications 등 테이블 부재 → V1__baseline.sql이 classpath에서 안 잡힘 또는 SQL 오류</li>
 *   <li>baseline 시나리오 검증 실패 → baseline-on-migrate / baseline-version 설정 문제</li>
 * </ul>
 *
 * <p><b>entity ↔ V1 schema 일치 검증은 본 클래스 책임 외</b> — Spring Boot 컨텍스트 부팅(+ Hibernate
 * ddl-auto: validate)이 필요하므로 별도 클래스 {@link FlywayEntitySchemaValidationIntegrationTest}에서 수행.
 */
@Testcontainers
class FlywayBaselineIntegrationTest {

    /** 본 테스트 전용 MySQL 컨테이너. AbstractIntegrationTest의 Singleton 컨테이너와 분리. */
    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    /**
     * 매 테스트 인스턴스에서 재사용하는 HikariDataSource.
     * {@code @BeforeEach}에서 1회 생성 + {@code @AfterEach}에서 close → 풀 생성/소멸 횟수 절반으로 감소.
     * (이전 버전은 BeforeEach/테스트 메서드 각각 newDataSource() → 1 케이스당 2 풀 생성/소멸)
     */
    private HikariDataSource dataSource;

    /**
     * 각 테스트 전에 데이터소스 + 컨테이너 schema 모두 초기화.
     * 두 테스트가 같은 testcontainers MySQL을 공유하므로 사용자 테이블 + flyway_schema_history까지
     * 모두 drop 해야 케이스 독립 보장. 본 메서드가 schema 격리의 단일 책임 지점.
     */
    @BeforeEach
    void setUp() {
        dataSource = newHikariDataSource();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        // information_schema에서 사용자 DB의 모든 테이블(flyway_schema_history 포함) 추출 후 DROP.
        // 테이블명에 백틱 포함 케이스는 사실상 없지만 정적 분석 경고 방지 위해 백틱 이스케이프 처리.
        jdbc.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                        String.class)
                .forEach(table -> jdbc.execute(
                        "DROP TABLE IF EXISTS `" + table.replace("`", "``") + "`"));
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    @Test
    @DisplayName("빈 DB: Flyway가 V1__baseline.sql을 실제 실행해 schema 생성 + schema_history에 success row 기록")
    void v1_applied_on_empty_db_creates_schema_and_records_success() {
        // 빈 DB 시나리오: baseline-on-migrate=false + baseline-version=0
        //   → V1(version=1) > baseline-version(=0) → V1을 일반 마이그레이션으로 실행
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .load();
        MigrateResult result = flyway.migrate();

        assertThat(result.success).as("마이그레이션이 성공해야 한다").isTrue();
        // V2(KAN-179) + V3(KAN-180) + V9(KAN-189) 합류 → V1 포함 총 4건.
        // 후속 버전 추가 시 본 가드의 기댓값도 함께 갱신해야 한다 (그 슬라이스 PR이 책임).
        assertThat(result.migrationsExecuted)
                .as("V1 + V2 + V3 + V9 + V202606031743 다섯 건 모두 적용되어야 한다")
                .isEqualTo(5);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // V1 + V2 + V3 + V9 row 모두 success 검증.
        // flyway_schema_history.success는 MySQL TINYINT(1) — Spring JdbcTemplate의 Boolean.class 자동
        // 변환은 Flyway 버전별 컬럼 타입 변경 시 깨질 위험. Integer.class로 받아 == 1로 비교가 안전.
        Integer v1Success = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'",
                Integer.class);
        assertThat(v1Success).isEqualTo(1);
        Integer v2Success = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '2'",
                Integer.class);
        assertThat(v2Success).isEqualTo(1);
        Integer v3Success = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '3'",
                Integer.class);
        assertThat(v3Success).isEqualTo(1);
        Integer v9Success = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '9'",
                Integer.class);
        assertThat(v9Success).isEqualTo(1);

        // V1 SQL이 실제 실행되어 테이블이 생성되었는지 sample 검증 (29개 중 대표 3개)
        assertTableExists(jdbc, "users");
        assertTableExists(jdbc, "wallets");
        assertTableExists(jdbc, "ad_applications");
        // V2가 추가한 admin_action_logs 테이블도 검증 (KAN-179 회귀 가드)
        assertTableExists(jdbc, "admin_action_logs");
        // V3가 users에 추가한 suspended_reason 컬럼도 검증 (KAN-180 회귀 가드)
        assertColumnExists(jdbc, "users", "suspended_reason");
        // V9가 추가한 faqs 테이블도 검증 (KAN-189 회귀 가드)
        assertTableExists(jdbc, "faqs");
        // V202606031743이 추가한 companion_reviews 테이블도 검증 (KAN-211 회귀 가드)
        assertTableExists(jdbc, "companion_reviews");
    }

    @Test
    @DisplayName("운영 prod baseline 시나리오: 기존 schema 있는 DB → V1을 BASELINE marker로만 기록, schema 변경 0")
    void baseline_on_migrate_records_v1_as_baseline_without_executing_sql() {
        // 기존 운영 schema가 있다고 가정 — 임의 테이블 생성으로 시뮬레이션
        // (Flyway가 schema 비어있지 않다고 판단 → baseline-on-migrate 발동)
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE existing_legacy_table (id BIGINT PRIMARY KEY)");
        // V3(KAN-180)가 users를 ALTER하므로 baseline 대상 기존 schema에 users가 존재해야 한다.
        // 실제 운영 prod baseline 시점엔 users 등 전체 schema가 이미 존재 — 이를 최소 테이블로 시뮬레이션.
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");

        // 운영 prod 설정 (application.yml 그대로): baseline-on-migrate=true + baseline-version=1
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .validateOnMigrate(true)
                .load();
        MigrateResult result = flyway.migrate();

        assertThat(result.success).as("baseline 적용이 성공해야 한다").isTrue();
        // V1은 BASELINE marker(실 실행 X). V2·V3·V9·V202606031743 모두 baseline-version=1보다 위
        // → 일반 마이그레이션으로 실 실행 → migrationsExecuted=4.
        // 후속 버전 추가 시 본 가드 기댓값도 함께 갱신해야 한다.
        assertThat(result.migrationsExecuted)
                .as("V1 baseline + V2·V3·V9·V202606031743 실 실행 → 4건")
                .isEqualTo(4);

        // schema_history에 V1은 BASELINE, V2는 SQL 타입으로 기록되어야 함
        String v1Type = jdbc.queryForObject(
                "SELECT type FROM flyway_schema_history WHERE version = '1'",
                String.class);
        assertThat(v1Type)
                .as("V1 row의 type이 BASELINE이어야 한다 (운영 prod 흐름)")
                .isEqualTo("BASELINE");
        String v2Type = jdbc.queryForObject(
                "SELECT type FROM flyway_schema_history WHERE version = '2'",
                String.class);
        assertThat(v2Type)
                .as("V2 row의 type이 SQL이어야 한다 (baseline 외 일반 마이그레이션)")
                .isEqualTo("SQL");
        String v3Type = jdbc.queryForObject(
                "SELECT type FROM flyway_schema_history WHERE version = '3'",
                String.class);
        assertThat(v3Type)
                .as("V3 row의 type이 SQL이어야 한다 (baseline 외 일반 마이그레이션)")
                .isEqualTo("SQL");

        // V1 SQL이 실행되지 않았는지 검증 — ad_applications 등 V1 dump 테이블이 없어야 함
        // (existing_legacy_table + 사전 생성 users만 존재, V1이 만드는 나머지 테이블은 부재)
        assertTableDoesNotExist(jdbc, "ad_applications");
        assertTableExists(jdbc, "existing_legacy_table"); // 기존 schema는 보존
        // V2는 baseline-version=1보다 위라 실 실행되므로 admin_action_logs 테이블은 존재해야 함
        assertTableExists(jdbc, "admin_action_logs");
        // V3도 실 실행되어 사전 생성 users에 suspended_reason 컬럼이 추가돼야 함 (KAN-180 회귀 가드)
        assertColumnExists(jdbc, "users", "suspended_reason");
        // V9도 실 실행되어 faqs 테이블 존재해야 함 (KAN-189 회귀 가드)
        assertTableExists(jdbc, "faqs");
        // V202606031743도 실 실행되어 companion_reviews 테이블 존재해야 함 (KAN-211 회귀 가드)
        assertTableExists(jdbc, "companion_reviews");
    }

    private static HikariDataSource newHikariDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setDriverClassName(MYSQL.getDriverClassName());
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }

    private static void assertTableExists(JdbcTemplate jdbc, String tableName) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Long.class,
                tableName);
        assertThat(count)
                .as(tableName + " 테이블이 존재해야 한다")
                .isEqualTo(1L);
    }

    private static void assertColumnExists(JdbcTemplate jdbc, String tableName, String columnName) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Long.class,
                tableName,
                columnName);
        assertThat(count)
                .as(tableName + "." + columnName + " 컬럼이 존재해야 한다")
                .isEqualTo(1L);
    }

    private static void assertTableDoesNotExist(JdbcTemplate jdbc, String tableName) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Long.class,
                tableName);
        assertThat(count)
                .as(tableName + " 테이블이 부재해야 한다 (baseline 시나리오)")
                .isEqualTo(0L);
    }
}
