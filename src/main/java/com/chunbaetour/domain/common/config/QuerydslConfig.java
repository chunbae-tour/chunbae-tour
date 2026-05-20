package com.chunbaetour.domain.common.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Querydsl 사용을 위한 {@link JPAQueryFactory} 빈 정의.
 *
 * <p>{@code com.chunbaetour.domain.place.repository.PlaceQueryRepository} 등 Querydsl 기반 Repository가
 * {@code JPAQueryFactory}를 생성자 주입받기 때문에 본 빈이 반드시 등록되어 있어야 한다 (S5 진입 시 누락 확인).
 *
 * <p>{@link PersistenceContext}로 트랜잭션-aware한 {@code EntityManager}를 주입받아, 호출 컨텍스트의 트랜잭션과
 * 동일한 1차 캐시/flush 정책을 공유한다.
 */
@Configuration
public class QuerydslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
