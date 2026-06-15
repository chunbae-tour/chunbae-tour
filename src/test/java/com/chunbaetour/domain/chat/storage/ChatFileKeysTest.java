package com.chunbaetour.domain.chat.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatFileKeysTest {

    @Test
    @DisplayName("objectKey — chat-rooms/{chatRoomId}/{uuid}.{ext} 형식, content-type별 확장자")
    void objectKey_format() {
        assertThat(ChatFileKeys.objectKey(10L, "image/jpeg")).matches("chat-rooms/10/[0-9a-fA-F\\-]{36}\\.jpg");
        assertThat(ChatFileKeys.objectKey(10L, "image/png")).matches("chat-rooms/10/[0-9a-fA-F\\-]{36}\\.png");
        assertThat(ChatFileKeys.objectKey(10L, "image/webp")).matches("chat-rooms/10/[0-9a-fA-F\\-]{36}\\.webp");
        assertThat(ChatFileKeys.objectKey(10L, "application/pdf")).matches("chat-rooms/10/[0-9a-fA-F\\-]{36}\\.pdf");
        assertThat(ChatFileKeys.objectKey(10L, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .matches("chat-rooms/10/[0-9a-fA-F\\-]{36}\\.docx");
        assertThat(ChatFileKeys.objectKey(10L, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .matches("chat-rooms/10/[0-9a-fA-F\\-]{36}\\.xlsx");
        assertThat(ChatFileKeys.objectKey(10L, "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                .matches("chat-rooms/10/[0-9a-fA-F\\-]{36}\\.pptx");
        assertThat(ChatFileKeys.objectKey(10L, "application/x-hwp")).matches("chat-rooms/10/[0-9a-fA-F\\-]{36}\\.hwp");
    }

    @Test
    @DisplayName("objectKey — 허용되지 않은 content-type → CHAT_FILE_TYPE_UNSUPPORTED")
    void objectKey_unsupported_throws() {
        assertThatThrownBy(() -> ChatFileKeys.objectKey(10L, "image/gif"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ChatFileKeys.objectKey(10L, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("belongsToChatRoom — 자기 채팅방 prefix만 true (IDOR 방지)")
    void belongsToChatRoom() {
        assertThat(ChatFileKeys.belongsToChatRoom("chat-rooms/10/uuid.jpg", 10L)).isTrue();
        assertThat(ChatFileKeys.belongsToChatRoom("chat-rooms/999/uuid.jpg", 10L)).isFalse(); // 타 채팅방
        assertThat(ChatFileKeys.belongsToChatRoom("arbitrary-key", 10L)).isFalse();           // 임의 객체
        assertThat(ChatFileKeys.belongsToChatRoom("chat-rooms/100/uuid.jpg", 10L)).isFalse(); // prefix 부분일치 방지
        assertThat(ChatFileKeys.belongsToChatRoom(null, 10L)).isFalse();
        assertThat(ChatFileKeys.belongsToChatRoom("chat-rooms/null/uuid.jpg", null)).isFalse(); // chatRoomId null 오인 매칭 차단
    }
}
