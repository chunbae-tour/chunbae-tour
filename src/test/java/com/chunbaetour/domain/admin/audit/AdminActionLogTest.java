package com.chunbaetour.domain.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AdminActionLog} entity 단위 테스트 (KAN-179).
 *
 * <p>Builder/팩토리에서 필수 필드 검증을 강제하는지 검증. KAN-141 PaymentOrderTest의 도메인 가드 패턴과 일관.
 */
class AdminActionLogTest {

    private static AdminActionContext validContext() {
        return new AdminActionContext(
                1L,
                AdminActionType.USER_SUSPEND,
                AdminTargetType.USER,
                100L,
                "spam-report-accumulated",
                "ACTIVE",
                "SUSPENDED");
    }

    @Test
    @DisplayName("from(context) — 필수 + 선택 필드 모두 매핑된다")
    void from_context_maps_all_fields() {
        AdminActionLog log = AdminActionLog.from(validContext());

        assertThat(log.getAdminUserId()).isEqualTo(1L);
        assertThat(log.getActionType()).isEqualTo(AdminActionType.USER_SUSPEND);
        assertThat(log.getTargetType()).isEqualTo(AdminTargetType.USER);
        assertThat(log.getTargetId()).isEqualTo(100L);
        assertThat(log.getReason()).isEqualTo("spam-report-accumulated");
        assertThat(log.getBeforeStatus()).isEqualTo("ACTIVE");
        assertThat(log.getAfterStatus()).isEqualTo("SUSPENDED");
        // createdAt은 JPA AuditingEntityListener가 persist 시점에 채움 — 단위 테스트에서는 null 정상
        assertThat(log.getCreatedAt()).isNull();
    }

    @Test
    @DisplayName("adminUserId null → IllegalArgumentException")
    void builder_rejects_null_adminUserId() {
        assertThatThrownBy(() -> AdminActionLog.builder()
                        .adminUserId(null)
                        .actionType(AdminActionType.USER_SUSPEND)
                        .targetType(AdminTargetType.USER)
                        .targetId(100L)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adminUserId");
    }

    @Test
    @DisplayName("actionType null → IllegalArgumentException")
    void builder_rejects_null_actionType() {
        assertThatThrownBy(() -> AdminActionLog.builder()
                        .adminUserId(1L)
                        .actionType(null)
                        .targetType(AdminTargetType.USER)
                        .targetId(100L)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionType");
    }

    @Test
    @DisplayName("targetType null → IllegalArgumentException")
    void builder_rejects_null_targetType() {
        assertThatThrownBy(() -> AdminActionLog.builder()
                        .adminUserId(1L)
                        .actionType(AdminActionType.USER_SUSPEND)
                        .targetType(null)
                        .targetId(100L)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetType");
    }

    @Test
    @DisplayName("targetId null → IllegalArgumentException")
    void builder_rejects_null_targetId() {
        assertThatThrownBy(() -> AdminActionLog.builder()
                        .adminUserId(1L)
                        .actionType(AdminActionType.USER_SUSPEND)
                        .targetType(AdminTargetType.USER)
                        .targetId(null)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetId");
    }

    @Test
    @DisplayName("선택 필드(reason/before/after) 모두 null이어도 정상 생성")
    void builder_allows_null_optional_fields() {
        AdminActionLog log = AdminActionLog.builder()
                .adminUserId(1L)
                .actionType(AdminActionType.USER_UNSUSPEND)
                .targetType(AdminTargetType.USER)
                .targetId(100L)
                .build();

        assertThat(log.getReason()).isNull();
        assertThat(log.getBeforeStatus()).isNull();
        assertThat(log.getAfterStatus()).isNull();
    }
}
