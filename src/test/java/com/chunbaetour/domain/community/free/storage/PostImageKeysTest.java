package com.chunbaetour.domain.community.free.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PostImageKeys 단위 테스트 (KAN-317) — 키 형식·소유 prefix 검증·확장자 매핑.
 */
class PostImageKeysTest {

    @Test
    @DisplayName("objectKey — posts/free/{userId}/{uuid}.{ext} 형식, 확장자는 content-type 파생")
    void objectKey_format() {
        assertThat(PostImageKeys.objectKey(7L, "image/jpeg")).matches("posts/free/7/[0-9a-fA-F\\-]{36}\\.jpg");
        assertThat(PostImageKeys.objectKey(7L, "image/png")).matches("posts/free/7/[0-9a-fA-F\\-]{36}\\.png");
        assertThat(PostImageKeys.objectKey(7L, "image/webp")).matches("posts/free/7/[0-9a-fA-F\\-]{36}\\.webp");
    }

    @Test
    @DisplayName("objectKey — 허용 안 되는 content-type → POST_IMAGE_TYPE_UNSUPPORTED")
    void objectKey_unsupported_throws() {
        assertThatThrownBy(() -> PostImageKeys.objectKey(7L, "image/gif"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_IMAGE_TYPE_UNSUPPORTED);
        assertThatThrownBy(() -> PostImageKeys.objectKey(7L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_IMAGE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("belongsToUser — 본인 prefix + 키 패턴(UUID·확장자) 모두 만족해야 true")
    void belongsToUser() {
        String valid = "posts/free/7/11111111-1111-1111-1111-111111111111.jpg";
        assertThat(PostImageKeys.belongsToUser(valid, 7L)).isTrue();
        assertThat(PostImageKeys.belongsToUser("posts/free/999/11111111-1111-1111-1111-111111111111.jpg", 7L))
                .isFalse(); // 타인 prefix
        assertThat(PostImageKeys.belongsToUser("posts/free/7/not-a-uuid.jpg", 7L)).isFalse(); // 패턴 위반(UUID 아님)
        assertThat(PostImageKeys.belongsToUser("posts/free/7/11111111-1111-1111-1111-111111111111.gif", 7L))
                .isFalse(); // 허용 안 되는 확장자
        assertThat(PostImageKeys.belongsToUser("shops/7/x.jpg", 7L)).isFalse(); // 타 도메인
        assertThat(PostImageKeys.belongsToUser(null, 7L)).isFalse();
        assertThat(PostImageKeys.belongsToUser(valid, null)).isFalse();
    }
}
