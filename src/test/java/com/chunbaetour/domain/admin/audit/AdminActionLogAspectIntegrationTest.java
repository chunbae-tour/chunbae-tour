package com.chunbaetour.domain.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * {@link AdminActionLogAspect} AOP 통합 테스트 (KAN-179).
 *
 * <p>{@link AbstractIntegrationTest} 상속 — testcontainers MySQL + Hibernate create-drop으로
 * {@code admin_action_logs} 테이블 자동 생성. 더미 service에 {@link LogAdminAction}을 부착해 advice가
 * 실제로 동작하는지 검증한다. MockMvc 없이 service를 직접 호출하므로 컨트롤러 매핑 부담 없음.
 *
 * <p>검증 케이스
 * <ol>
 *   <li>정상 호출 → {@code admin_action_logs} row 1건 + 필드 정확</li>
 *   <li>service 메서드가 RuntimeException → row 0건 (advice가 record skip)</li>
 *   <li>repository.save 강제 실패 → 호출자 예외 없이 흡수 + log.error 캡처</li>
 *   <li>SecurityContext 없음 → adminUserId null → record skip → row 0건</li>
 *   <li>readOnly 트랜잭션 → record 가드 미발행 → row 0건</li>
 *   <li>BusinessException(4xx) 전파 → record 호출 X → row 0건</li>
 * </ol>
 */
@SpringBootTest
@Import(AdminActionLogAspectIntegrationTest.DummyAdminAuditedService.class)
class AdminActionLogAspectIntegrationTest extends AbstractIntegrationTest {

    private static final Long ADMIN_USER_ID = 999L;

    @Autowired
    private DummyAdminAuditedService dummyService;

    @MockitoSpyBean
    private AdminActionLogRepository repository;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setupSecurityAndLogger() {
        // SecurityContext에 운영자 principal 주입 — advice가 SecurityContextHolder에서 adminUserId 추출
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        ADMIN_USER_ID,
                        null,
                        AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

        // RequestContext에 path variable Map 주입 — advice가 targetId를 path에서 추출
        // 실제 admin 컨트롤러는 Spring MVC가 HandlerMapping을 통해 path variable을 자동 주입하지만,
        // service 직접 호출 환경에서는 수동으로 채워야 동일 시나리오 재현 가능.
        Map<String, String> pathVars = new HashMap<>();
        pathVars.put("userId", "100");
        HttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVars);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Logger serviceLogger = (Logger) LoggerFactory.getLogger(AdminActionLogService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void cleanup() {
        // spy를 reset해 다음 케이스에 영향 없음
        org.mockito.Mockito.reset(repository);
        repository.deleteAll();
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        if (logAppender != null) {
            Logger serviceLogger = (Logger) LoggerFactory.getLogger(AdminActionLogService.class);
            serviceLogger.detachAppender(logAppender);
        }
    }

    @Test
    @DisplayName("정상 호출 → admin_action_logs row 1건 + 필드 정확")
    void normal_invocation_records_single_row() {
        dummyService.suspend();

        var rows = repository.findAll();
        assertThat(rows).hasSize(1);
        AdminActionLog row = rows.getFirst();
        assertThat(row.getAdminUserId()).isEqualTo(ADMIN_USER_ID);
        assertThat(row.getActionType()).isEqualTo(AdminActionType.USER_SUSPEND);
        assertThat(row.getTargetType()).isEqualTo(AdminTargetType.USER);
        assertThat(row.getTargetId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("service 예외 → advice가 record 호출 X → row 0건")
    void service_exception_skips_record() {
        assertThatThrownBy(() -> dummyService.suspendAndFail())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated");

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("SecurityContext 없음 → adminUserId null → record skip → row 0건")
    void unauthenticated_context_skips_record() {
        SecurityContextHolder.clearContext();

        dummyService.suspend();

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("readOnly 트랜잭션 → record 가드가 미발행 → row 0건")
    void readOnly_transaction_skips_record() {
        dummyService.suspendReadOnly();

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("BusinessException(4xx) 전파 → advice가 record 호출 X → row 0건")
    void business_exception_propagates_and_skips_record() {
        assertThatThrownBy(() -> dummyService.suspendBusinessFail())
                .isInstanceOf(BusinessException.class);

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("fixedTargetId=0 bulk 액션 — path variable 없어도 targetId=0으로 로그 기록")
    void fixedTargetId_bulk_action_records_with_zero_target() {
        // path variable 없는 환경 재현 — MARKET_SYNC처럼 /sync 경로에 path var 없음
        MockHttpServletRequest emptyRequest = new MockHttpServletRequest();
        emptyRequest.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, new HashMap<>());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(emptyRequest));

        dummyService.syncBulk();

        var rows = repository.findAll();
        assertThat(rows).hasSize(1);
        AdminActionLog row = rows.getFirst();
        assertThat(row.getActionType()).isEqualTo(AdminActionType.MARKET_SYNC);
        assertThat(row.getTargetType()).isEqualTo(AdminTargetType.MARKET);
        assertThat(row.getTargetId()).isEqualTo(0L);
    }

    @Test
    @DisplayName("repository.save 강제 실패 → 호출자 예외 없음 + 로그 흡수")
    void save_failure_absorbed_silently() {
        org.mockito.Mockito.doThrow(new RuntimeException("simulated DB outage"))
                .when(repository).save(org.mockito.ArgumentMatchers.any());

        // 호출자는 정상 종료 — advice의 흡수 패턴 검증
        dummyService.suspend();

        boolean foundErrorLog = logAppender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.ERROR
                        && e.getFormattedMessage().contains("AdminActionLog save failed"));
        assertThat(foundErrorLog)
                .as("save 실패 시 log.error 1건 이상 발생해야 한다 (흡수 확인)")
                .isTrue();
    }

    /**
     * 테스트 전용 더미 service — {@link LogAdminAction} 부착 메서드 2개.
     *
     * <p>{@link Transactional} 부착 — 정상 메서드는 commit 후 afterCommit 콜백으로 save 진행.
     * 예외 메서드는 rollback + advice가 메서드 예외 시 record 호출 자체를 skip → 두 경로 모두 검증.
     */
    @Component
    @TestConfiguration
    public static class DummyAdminAuditedService {

        @Transactional
        @LogAdminAction(actionType = AdminActionType.USER_SUSPEND, targetType = AdminTargetType.USER)
        public void suspend() {
            // 비즈니스 로직 자리 — no-op
        }

        @Transactional
        @LogAdminAction(actionType = AdminActionType.USER_SUSPEND, targetType = AdminTargetType.USER)
        public void suspendAndFail() {
            throw new RuntimeException("simulated service failure");
        }

        /** readOnly 트랜잭션 — record() 가드가 미발행 처리해야 한다. */
        @Transactional(readOnly = true)
        @LogAdminAction(actionType = AdminActionType.USER_SUSPEND, targetType = AdminTargetType.USER)
        public void suspendReadOnly() {
            // 조회성 메서드 자리 — no-op
        }

        /** 4xx 비즈니스 예외 — advice가 record 호출 전에 그대로 전파해야 한다. */
        @Transactional
        @LogAdminAction(actionType = AdminActionType.USER_SUSPEND, targetType = AdminTargetType.USER)
        public void suspendBusinessFail() {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        /** bulk 액션 — path variable 없이 fixedTargetId = 0L 고정 */
        @Transactional
        @LogAdminAction(actionType = AdminActionType.MARKET_SYNC, targetType = AdminTargetType.MARKET,
                fixedTargetId = 0L)
        public void syncBulk() {
            // no-op
        }

        @Bean
        public Object noOpBeanToKeepConfig() {
            return new Object();
        }
    }
}
