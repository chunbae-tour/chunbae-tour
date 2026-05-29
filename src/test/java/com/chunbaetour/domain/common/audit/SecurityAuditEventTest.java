package com.chunbaetour.domain.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link SecurityAuditEvent} record compact constructor 검증.
 *
 * <p>핵심 회귀 방지:
 * <ul>
 *   <li>timestamp/eventType null 거부 (호출자 실수 차단)</li>
 *   <li>outcome은 SUCCESS/FAILURE만 허용 (SIEM 룰 호환성)</li>
 *   <li>userAgent 자동 잘림 (200자 초과)</li>
 *   <li>metadata null → 빈 맵 정규화</li>
 * </ul>
 */
class SecurityAuditEventTest {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void timestamp_null_거부() {
        assertThatThrownBy(() -> new SecurityAuditEvent(
                null, SecurityAuditEventType.LOGIN_SUCCESS, 1L, null, null, null,
                SecurityAuditEvent.OUTCOME_SUCCESS, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void eventType_null_거부() {
        assertThatThrownBy(() -> new SecurityAuditEvent(
                NOW, null, 1L, null, null, null,
                SecurityAuditEvent.OUTCOME_SUCCESS, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void outcome_표준값_외_거부() {
        assertThatThrownBy(() -> new SecurityAuditEvent(
                NOW, SecurityAuditEventType.LOGIN_SUCCESS, 1L, null, null, null,
                "PARTIAL", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome");
    }

    @Test
    void outcome_lowercase_거부() {
        assertThatThrownBy(() -> new SecurityAuditEvent(
                NOW, SecurityAuditEventType.LOGIN_SUCCESS, 1L, null, null, null,
                "success", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userAgent_200자_초과_자동_잘림() {
        String longUserAgent = "x".repeat(500);
        SecurityAuditEvent event = new SecurityAuditEvent(
                NOW, SecurityAuditEventType.LOGIN_SUCCESS, 1L, null, null, longUserAgent,
                SecurityAuditEvent.OUTCOME_SUCCESS, null, Map.of());
        assertThat(event.userAgent()).hasSize(SecurityAuditEvent.USER_AGENT_MAX_LENGTH);
    }

    @Test
    void metadata_null이면_빈맵으로_정규화() {
        SecurityAuditEvent event = new SecurityAuditEvent(
                NOW, SecurityAuditEventType.LOGIN_SUCCESS, 1L, null, null, null,
                SecurityAuditEvent.OUTCOME_SUCCESS, null, null);
        assertThat(event.metadata()).isEqualTo(Collections.emptyMap());
    }

    @Test
    void metadata_불변_복사() {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("k", "v");
        SecurityAuditEvent event = new SecurityAuditEvent(
                NOW, SecurityAuditEventType.LOGIN_SUCCESS, 1L, null, null, null,
                SecurityAuditEvent.OUTCOME_SUCCESS, null, mutable);
        // 원본 mutation이 event에 영향 주면 안 됨
        mutable.put("k2", "v2");
        assertThat(event.metadata()).hasSize(1).containsEntry("k", "v");
    }

    @Test
    void 정상_생성_경로() {
        SecurityAuditEvent event = new SecurityAuditEvent(
                NOW, SecurityAuditEventType.LOGIN_FAILURE, 42L, null, "192.168.0.***", "ua",
                SecurityAuditEvent.OUTCOME_FAILURE, "AUTH_001", Map.of("requiredRole", "USER"));
        assertThat(event.eventType()).isEqualTo(SecurityAuditEventType.LOGIN_FAILURE);
        assertThat(event.actorId()).isEqualTo(42L);
        assertThat(event.outcome()).isEqualTo("FAILURE");
        assertThat(event.reason()).isEqualTo("AUTH_001");
    }
}
