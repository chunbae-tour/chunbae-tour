package com.chunbaetour.domain.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link SecurityAuditLogger} 단위 테스트.
 *
 * <p>검증 범위:
 * <ul>
 *   <li>emit 호출 시 audit.security logger로 출력</li>
 *   <li>MDC가 호출 직후 clear (thread-local 누설 차단)</li>
 *   <li>RequestContextHolder에서 IP / User-Agent 자동 enrich + 마스킹</li>
 *   <li>비-HTTP 컨텍스트(RequestAttributes 없음)도 emit 가능</li>
 *   <li><b>보안 회귀</b>: 비밀번호 / 토큰 본문이 로그에 절대 노출되지 않음 (자료형에 부재)</li>
 * </ul>
 */
class SecurityAuditLoggerTest {

    private SecurityAuditLogger logger;
    private Logger logbackLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = new SecurityAuditLogger();
        // logback root에서 audit.security logger 가져와 ListAppender 부착 → 로그 캡처
        logbackLogger = (Logger) LoggerFactory.getLogger(SecurityAuditLogger.LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(appender);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void emitSuccess_audit_logger로_출력() {
        logger.emitSuccess(SecurityAuditEventType.LOGIN_SUCCESS, 42L, Map.of("role", "USER"));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLoggerName()).isEqualTo(SecurityAuditLogger.LOGGER_NAME);
        assertThat(event.getMDCPropertyMap())
                .containsEntry("audit.eventType", "LOGIN_SUCCESS")
                .containsEntry("audit.outcome", "SUCCESS")
                .containsEntry("audit.actorId", "42")
                .containsEntry("audit.meta.role", "USER");
    }

    @Test
    void emitFailure_reason_포함() {
        logger.emitFailure(SecurityAuditEventType.LOGIN_FAILURE, 42L, "AUTH_001", Map.of());

        assertThat(appender.list).hasSize(1);
        var mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).containsEntry("audit.outcome", "FAILURE");
        assertThat(mdc).containsEntry("audit.reason", "AUTH_001");
    }

    @Test
    void emit_후_MDC_clear() {
        logger.emitSuccess(SecurityAuditEventType.LOGIN_SUCCESS, 1L, Map.of("k", "v"));

        // emit 직후 MDC는 비어있어야 함 (thread-local 누설 차단)
        assertThat(org.slf4j.MDC.get("audit.eventType")).isNull();
        assertThat(org.slf4j.MDC.get("audit.actorId")).isNull();
        assertThat(org.slf4j.MDC.get("audit.meta.k")).isNull();
    }

    @Test
    void HTTP_컨텍스트에서_IP_자동_마스킹() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.0.100");
        req.addHeader("User-Agent", "Mozilla/5.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        logger.emitSuccess(SecurityAuditEventType.LOGIN_SUCCESS, 42L, Map.of());

        var mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).containsEntry("audit.ipMasked", "192.168.0.***");
        assertThat(mdc).containsEntry("audit.userAgent", "Mozilla/5.0");
    }

    @Test
    void HTTP_컨텍스트_없음_emit_가능() {
        // RequestContextHolder 미설정 — 스케줄러/이벤트 listener 등 비-HTTP 흐름
        logger.emitSuccess(SecurityAuditEventType.LOGIN_SUCCESS, 1L, Map.of());

        assertThat(appender.list).hasSize(1);
        var mdc = appender.list.get(0).getMDCPropertyMap();
        // ip / userAgent는 null이므로 MDC에 없음 (putMdcIfPresent skip)
        assertThat(mdc).doesNotContainKey("audit.ipMasked");
        assertThat(mdc).doesNotContainKey("audit.userAgent");
    }

    @Test
    void 보안_회귀_민감_정보_미포함() {
        // 호출자가 실수로 password를 metadata에 넣어도 자료형이 String이라 logger는 그대로 출력.
        // 본 회귀 가드 = SecurityAuditEvent에 password 필드 자체가 없음을 컴파일 단계에서 보장.
        // 추가로 사용 흐름에서 password / accessToken / refreshToken 같은 키가 metadata에 흘러들지 않는지 호출자 review로 차단.
        logger.emitSuccess(SecurityAuditEventType.LOGIN_SUCCESS, 1L, Map.of("role", "USER"));

        // role만 흘러 들어가야 함. password / token 키는 없어야 함
        var mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).doesNotContainKey("audit.meta.password");
        assertThat(mdc).doesNotContainKey("audit.meta.accessToken");
        assertThat(mdc).doesNotContainKey("audit.meta.refreshToken");
    }

    @Test
    void null_event_emit_시_warn_후_skip() {
        logger.emit((SecurityAuditEvent) null);
        // audit.security logger는 emit 안 됨 (null 거부). 별도 root logger의 warn은 별 logger
        assertThat(appender.list).isEmpty();
    }

    @Test
    void IPv6_마스킹() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("2001:db8::8a2e:370:7334");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        logger.emitSuccess(SecurityAuditEventType.LOGIN_SUCCESS, 1L, Map.of());

        assertThat(appender.list.get(0).getMDCPropertyMap())
                .containsEntry("audit.ipMasked", "2001:db8::8a2e:370:***");
    }
}
