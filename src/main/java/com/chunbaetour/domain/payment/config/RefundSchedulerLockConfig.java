package com.chunbaetour.domain.payment.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 환불 스케줄러 분산 락 설정 (KAN-205).
 *
 * <p>다중 인스턴스 배포 시 {@link net.javacrumbs.shedlock.spring.annotation.SchedulerLock}이
 * 붙은 @Scheduled 메서드를 동시에 한 인스턴스만 실행하도록 보장한다.
 * shedlock 테이블을 공유 DB에 두고 DB 시간 기준으로 락을 관리해 인스턴스 간 시계 편차를 무시한다.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT3M")
public class RefundSchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime() // 인스턴스 간 시계 편차 무시 — DB 서버 시간 기준
                        .build()
        );
    }
}
