package com.chunbaetour.domain.cs.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SupportFileKeysTest {

    @Test
    @DisplayName("objectKey — support-rooms/{supportRoomId}/{uuid}.{ext} 형식, content-type별 확장자")
    void objectKey_format() {
        assertThat(SupportFileKeys.objectKey(10L, "image/jpeg")).matches("support-rooms/10/[0-9a-fA-F\\-]{36}\\.jpg");
        assertThat(SupportFileKeys.objectKey(10L, "image/png")).matches("support-rooms/10/[0-9a-fA-F\\-]{36}\\.png");
        assertThat(SupportFileKeys.objectKey(10L, "image/webp")).matches("support-rooms/10/[0-9a-fA-F\\-]{36}\\.webp");
        assertThat(SupportFileKeys.objectKey(10L, "application/pdf")).matches("support-rooms/10/[0-9a-fA-F\\-]{36}\\.pdf");
        assertThat(SupportFileKeys.objectKey(10L, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .matches("support-rooms/10/[0-9a-fA-F\\-]{36}\\.docx");
        assertThat(SupportFileKeys.objectKey(10L, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .matches("support-rooms/10/[0-9a-fA-F\\-]{36}\\.xlsx");
        assertThat(SupportFileKeys.objectKey(10L, "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                .matches("support-rooms/10/[0-9a-fA-F\\-]{36}\\.pptx");
        assertThat(SupportFileKeys.objectKey(10L, "application/x-hwp")).matches("support-rooms/10/[0-9a-fA-F\\-]{36}\\.hwp");
    }

    @Test
    @DisplayName("objectKey — 허용되지 않은 content-type → SUPPORT_FILE_TYPE_UNSUPPORTED")
    void objectKey_unsupported_throws() {
        assertThatThrownBy(() -> SupportFileKeys.objectKey(10L, "image/gif"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> SupportFileKeys.objectKey(10L, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("belongsToSupportRoom — 자기 상담방 prefix만 true (IDOR 방지)")
    void belongsToSupportRoom() {
        assertThat(SupportFileKeys.belongsToSupportRoom("support-rooms/10/uuid.jpg", 10L)).isTrue();
        assertThat(SupportFileKeys.belongsToSupportRoom("support-rooms/999/uuid.jpg", 10L)).isFalse(); // 타 상담방
        assertThat(SupportFileKeys.belongsToSupportRoom("arbitrary-key", 10L)).isFalse();               // 임의 객체
        assertThat(SupportFileKeys.belongsToSupportRoom("support-rooms/100/uuid.jpg", 10L)).isFalse();  // prefix 부분일치 방지
        assertThat(SupportFileKeys.belongsToSupportRoom(null, 10L)).isFalse();
        assertThat(SupportFileKeys.belongsToSupportRoom("support-rooms/null/uuid.jpg", null)).isFalse(); // supportRoomId null 오인 매칭 차단
    }
}
