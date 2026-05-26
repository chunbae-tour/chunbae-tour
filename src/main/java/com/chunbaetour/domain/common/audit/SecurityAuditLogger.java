package com.chunbaetour.domain.common.audit;

import com.chunbaetour.domain.common.util.IpMaskUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 보안 감사 로그 진입점 (KAN-105 Epic KAN-64 S4).
 *
 * <p>logger name {@code audit.security} 고정 — {@code logback-spring.xml}에서 별도 file/stdout JSON
 * appender로 매핑된다. 일반 application 로그와 분리되어 보존 정책(1년) + SIEM 수집을 별도로 적용 가능.
 *
 * <p>호출자 책임 = {@link SecurityAuditEvent} 객체를 만들고 {@link #emit}에 전달. 단일 진입점이라
 * 포맷 불일치/누락 사고 방지.
 *
 * <p>출력 방식: SLF4J + MDC. SecurityAuditEvent 필드를 MDC 키로 펼쳐 logstash-logback-encoder가 JSON
 * 최상위 필드로 직렬화. MDC는 try-finally로 즉시 clear → 다른 thread/요청에 누설 차단.
 *
 * <p>예외 안전성: emit 실패(I/O 등)는 audit logger가 흡수해야 한다 (호출자 비즈니스 흐름에 영향 금지).
 * Logback의 file appender는 기본적으로 RuntimeException 전파하지 않으므로 추가 try/catch 불필요.
 *
 * <p>운영 사용 시 검색 키워드: {@code audit.security} logger + {@code eventType} 필드.
 */
@Component
public class SecurityAuditLogger {

    /** logback-spring.xml 매핑과 정확히 일치해야 함 (변경 시 양쪽 동기 갱신). */
    public static final String LOGGER_NAME = "audit.security";

    /** audit 채널 logger — 정상 이벤트 출력 전용. */
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    /** 내부 진단 logger — emit 호출자 오류(null 등) 경고. audit 채널과 분리해 SIEM 노이즈 차단. */
    private static final Logger internalLog = LoggerFactory.getLogger(SecurityAuditLogger.class);

    /** MDC 키 prefix — 일반 application MDC 키와 충돌 방지 + Prometheus query에서 grouping 일관. */
    private static final String MDC_PREFIX = "audit.";

    /**
     * 성공 이벤트 emit helper — 호출자는 비즈니스 필드만 채우면 됨.
     *
     * <p>IP / UserAgent는 현재 HTTP 요청({@link RequestContextHolder})에서 자동 추출 + 마스킹.
     * 비-HTTP 컨텍스트(스케줄러/이벤트 listener 등)에서 호출 시 둘 다 null로 emit (audit 손실 없음).
     */
    public void emitSuccess(SecurityAuditEventType type, Long actorId, Map<String, String> metadata) {
        emit(buildEvent(type, SecurityAuditEvent.OUTCOME_SUCCESS, actorId, null, metadata));
    }

    /** 실패 이벤트 emit helper — reason 필수. */
    public void emitFailure(SecurityAuditEventType type, Long actorId, String reason, Map<String, String> metadata) {
        emit(buildEvent(type, SecurityAuditEvent.OUTCOME_FAILURE, actorId, reason, metadata));
    }

    private SecurityAuditEvent buildEvent(
            SecurityAuditEventType type,
            String outcome,
            Long actorId,
            String reason,
            Map<String, String> metadata) {
        HttpServletRequest req = currentRequest();
        String ipMasked = req != null ? IpMaskUtil.mask(req.getRemoteAddr()) : null;
        String userAgent = req != null ? req.getHeader(HttpHeaders.USER_AGENT) : null;
        return new SecurityAuditEvent(
                Instant.now(), type, actorId, null, ipMasked, userAgent, outcome, reason, metadata);
    }

    /**
     * 현재 thread에 바인딩된 HTTP 요청. 비-HTTP 컨텍스트에서는 null.
     */
    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest();
        }
        return null;
    }

    /**
     * 감사 이벤트 출력 (raw record 직접 사용 — 특수한 경우만. 일반적으로 {@link #emitSuccess}/{@link #emitFailure} 사용).
     *
     * <p>{@link SecurityAuditEvent} 필드를 MDC에 펼쳐 JSON encoder가 최상위 필드로 직렬화하게 한다.
     * try-finally로 MDC를 호출 직후 clear → thread-local 누설 차단.
     *
     * <p>{@code metadata}는 nested 객체 형태로 인코딩되지 않고 {@code audit.meta.<key>} 평탄화로 풀어
     * SIEM/Kibana 쿼리 시 dot-notation 없이 단순 필드 접근 가능.
     *
     * @param event 발행할 감사 이벤트 (null 금지 — compact ctor가 이미 검증)
     */
    public void emit(SecurityAuditEvent event) {
        if (event == null) {
            // 호출자 실수 — audit 누락이 비즈니스 흐름을 깨면 안 되므로 throw 대신 warn 후 return.
            // 내부 진단 logger로 출력 — audit 채널 노이즈 차단.
            internalLog.warn("SecurityAuditLogger.emit(null) — 호출자 검토 필요. 이벤트 미발행.");
            return;
        }
        try {
            putMdc("timestamp", event.timestamp().toString());
            putMdc("eventType", event.eventType().name());
            putMdc("outcome", event.outcome());
            putMdcIfPresent("actorId", event.actorId());
            putMdcIfPresent("targetUserId", event.targetUserId());
            putMdcIfPresent("ipMasked", event.ipMasked());
            putMdcIfPresent("userAgent", event.userAgent());
            putMdcIfPresent("reason", event.reason());
            event.metadata().forEach((k, v) -> putMdc("meta." + k, v));

            // 메시지 본문은 검색 fallback용. 주요 정보는 MDC(JSON 최상위 필드)로 출력.
            log.info("{} {}", event.eventType().name(), event.outcome());
        } finally {
            clearMdc();
        }
    }

    private void putMdc(String key, String value) {
        MDC.put(MDC_PREFIX + key, value);
    }

    private void putMdcIfPresent(String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        MDC.put(MDC_PREFIX + key, value.toString());
    }

    private void clearMdc() {
        MDC.remove(MDC_PREFIX + "timestamp");
        MDC.remove(MDC_PREFIX + "eventType");
        MDC.remove(MDC_PREFIX + "outcome");
        MDC.remove(MDC_PREFIX + "actorId");
        MDC.remove(MDC_PREFIX + "targetUserId");
        MDC.remove(MDC_PREFIX + "ipMasked");
        MDC.remove(MDC_PREFIX + "userAgent");
        MDC.remove(MDC_PREFIX + "reason");
        // meta.* 키는 호출별 다르므로 prefix scan으로 모두 제거
        java.util.Map<String, String> all = MDC.getCopyOfContextMap();
        if (all != null) {
            all.keySet().stream()
                    .filter(k -> k.startsWith(MDC_PREFIX + "meta."))
                    .forEach(MDC::remove);
        }
    }
}
