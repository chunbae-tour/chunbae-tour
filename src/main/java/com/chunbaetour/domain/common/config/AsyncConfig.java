package com.chunbaetour.domain.common.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 작업 실행기 설정 (KAN-262).
 *
 * <p>관리자 관광지 동기화처럼 오래 걸리는 작업을 요청 스레드와 분리해 백그라운드로 실행한다.
 * 단일 인스턴스 기준 ShedLock으로 중복 실행을 막으므로 큐/풀은 작게 잡는다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 관광지 동기화 전용 실행기 — 동시 1개 작업만 의미 있으므로(ShedLock) 풀/큐를 작게 둔다. */
    @Bean("placeSyncExecutor")
    public Executor placeSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("place-sync-");
        executor.initialize();
        return executor;
    }
}
