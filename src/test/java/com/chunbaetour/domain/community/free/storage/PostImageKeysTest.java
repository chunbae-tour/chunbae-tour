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
    @DisplayName("belongsToUser — 본인 prefix만 true, 타인/임의/null은 false")
    void belongsToUser() {
        assertThat(PostImageKeys.belongsToUser("posts/free/7/x.jpg", 7L)).isTrue();
        assertThat(PostImageKeys.belongsToUser("posts/free/999/x.jpg", 7L)).isFalse(); // 타인
        assertThat(PostImageKeys.belongsToUser("shops/7/x.jpg", 7L)).isFalse();        // 타 도메인 키
        assertThat(PostImageKeys.belongsToUser(null, 7L)).isFalse();
        assertThat(PostImageKeys.belongsToUser("posts/free/7/x.jpg", null)).isFalse();
    }
}
