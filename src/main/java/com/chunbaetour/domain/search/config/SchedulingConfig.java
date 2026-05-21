package com.chunbaetour.domain.search.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 검색 도메인 스케줄링 활성화 설정.
 * <p>
 * {@link EnableScheduling}을 선언하여 Spring의 태스크 스케줄러를 활성화한다.
 * 이 설정이 없으면 {@code @Scheduled} 애노테이션이 동작하지 않는다.
 * </p>
 *
 * <p>
 * 적용 대상:
 * <ul>
 *   <li>{@link com.chunbaetour.domain.search.service.PopularSearchService#resetDailyRanking()}
 *       — 매일 자정 인기 검색어 일간 초기화</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>주의</b>: {@code @EnableScheduling}은 전체 애플리케이션에 적용되는 글로벌 설정이다.
 * 추후 다른 도메인에서도 스케줄러를 사용할 경우 이 설정 하나로 공유된다.
 * 만약 팀 공통 config 패키지로 이관이 필요하면 {@code common/config}로 옮기는 것을 권장한다.
 * </p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // 빈 설정 클래스: @EnableScheduling 선언 목적
}
