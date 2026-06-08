package com.chunbaetour.domain.admin.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Admin audit 인프라 Bean 설정 (KAN-179, Admin Epic KAN-177 S01).
 *
 * <p>현재 단 한 가지 Bean — REQUIRES_NEW 전용 {@link TransactionTemplate}을 제공한다.
 * {@link AdminActionLogService}가 본 요청 트랜잭션과 독립된 새 트랜잭션을 열어 로그를 save할 때 사용.
 *
 * <p>REQUIRES_NEW 정책 — 호출 시점에 진행 중인 트랜잭션이 있어도 잠시 보류시키고 새 트랜잭션을 시작.
 * 로그 save 트랜잭션은 본 요청 트랜잭션과 commit/rollback이 분리된다. 로그 save 실패가 본 요청 트랜잭션에
 * 영향을 주지 않는다.
 */
@Configuration
public class AdminAuditConfig {

    /** {@link AdminActionLogService}에 주입되는 Bean 이름 — 다른 TransactionTemplate과 구분하기 위해 명시. */
    public static final String REQUIRES_NEW_TEMPLATE_BEAN = "adminAuditRequiresNewTransactionTemplate";

    @Bean(name = REQUIRES_NEW_TEMPLATE_BEAN)
    public TransactionTemplate adminAuditRequiresNewTransactionTemplate(PlatformTransactionManager txManager) {
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
