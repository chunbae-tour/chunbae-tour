package com.chunbaetour.infrastructure.flyway;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
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
 */
@Testcontainers
class FlywayBaselineIntegrationTest {

    /** 본 테스트 전용 MySQL 컨테이너. AbstractIntegrationTest의 Singleton 컨테이너와 분리. */
    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @Test
    @DisplayName("빈 DB: Flyway가 V1__baseline.sql을 실제 실행해 schema 생성 + schema_history에 success row 기록")
    void v1_applied_on_empty_db_creates_schema_and_records_success() {
        // 빈 DB 시나리오: baseline-on-migrate=false + baseline-version=0
        //   → V1(version=1) > baseline-version(=0) → V1을 일반 마이그레이션으로 실행
        DataSource ds = newDataSource();
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .baselineVersion("0")
                    .validateOnMigrate(true)
                    .load();
            MigrateResult result = flyway.migrate();

            assertThat(result.success).as("V1 마이그레이션이 성공해야 한다").isTrue();
            assertThat(result.migrationsExecuted)
                    .as("정확히 1건(V1)이 적용되어야 한다")
                    .isEqualTo(1);

            JdbcTemplate jdbc = new JdbcTemplate(ds);

            // V1 row 검증
            Boolean v1Success = jdbc.queryForObject(
                    "SELECT success FROM flyway_schema_history WHERE version = '1'",
                    Boolean.class);
            assertThat(v1Success)
                    .as("flyway_schema_history에 V1 row가 success=true로 기록되어야 한다")
                    .isTrue();

            // V1 SQL이 실제 실행되어 테이블이 생성되었는지 sample 검증 (29개 중 대표 3개)
            assertTableExists(jdbc, "users");
            assertTableExists(jdbc, "wallets");
            assertTableExists(jdbc, "ad_applications");
        } finally {
            closeQuietly(ds);
        }
    }

    @Test
    @DisplayName("운영 prod baseline 시나리오: 기존 schema 있는 DB → V1을 BASELINE marker로만 기록, schema 변경 0")
    void baseline_on_migrate_records_v1_as_baseline_without_executing_sql() {
        DataSource ds = newDataSource();
        try {
            // 기존 운영 schema가 있다고 가정 — 임의 테이블 생성으로 시뮬레이션
            // (Flyway가 schema 비어있지 않다고 판단 → baseline-on-migrate 발동)
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE existing_legacy_table (id BIGINT PRIMARY KEY)");

            // 운영 prod 설정 (application.yml 그대로): baseline-on-migrate=true + baseline-version=1
            Flyway flyway = Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .validateOnMigrate(true)
                    .load();
            MigrateResult result = flyway.migrate();

            assertThat(result.success).as("baseline 적용이 성공해야 한다").isTrue();
            // baseline은 BASELINE 타입 row 1건만 기록 (V1 SQL 실행 X) → migrationsExecuted=0
            assertThat(result.migrationsExecuted)
                    .as("V1은 baseline marker로만 기록되어 실제 실행은 0건이어야 한다")
                    .isEqualTo(0);

            // schema_history에 BASELINE 타입 row가 존재해야 함
            String v1Type = jdbc.queryForObject(
                    "SELECT type FROM flyway_schema_history WHERE version = '1'",
                    String.class);
            assertThat(v1Type)
                    .as("V1 row의 type이 BASELINE이어야 한다 (운영 prod 흐름)")
                    .isEqualTo("BASELINE");

            // V1 SQL이 실행되지 않았는지 검증 — ad_applications 등 dump 테이블이 없어야 함
            // (existing_legacy_table만 있고 V1의 어떤 테이블도 생성되지 않음)
            assertTableDoesNotExist(jdbc, "ad_applications");
            assertTableDoesNotExist(jdbc, "users");
            assertTableExists(jdbc, "existing_legacy_table"); // 기존 schema는 보존
        } finally {
            closeQuietly(ds);
        }
    }

    private static DataSource newDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setDriverClassName(MYSQL.getDriverClassName());
        config.setMaximumPoolSize(2);
        // 본 테스트는 매번 새 DataSource로 격리한 후 close — 컨테이너는 재사용하되 schema는 매 테스트 전에
        // 초기화해야 한다. 두 케이스 모두 자체적으로 schema를 손대므로 컨테이너 자체를 매 케이스 전에 초기화.
        return new HikariDataSource(config);
    }

    /**
     * 각 테스트 전에 컨테이너의 사용자 schema를 비운다. 두 테스트가 같은 컨테이너를 공유하므로,
     * @BeforeEach로 모든 사용자 테이블을 drop 해야 케이스 독립 보장.
     */
    @org.junit.jupiter.api.BeforeEach
    void resetSchema() {
        DataSource ds = newDataSource();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
            // information_schema 조회로 사용자 DB의 모든 테이블 이름 추출 후 DROP
            jdbc.queryForList(
                            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                            String.class)
                    .forEach(table -> jdbc.execute("DROP TABLE IF EXISTS `" + table + "`"));
            jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        } finally {
            closeQuietly(ds);
        }
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

    private static void closeQuietly(DataSource ds) {
        if (ds instanceof HikariDataSource hikari) {
            hikari.close();
        }
    }
}
