package com.chunbaetour.domain.auth.profileimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ProfileImageKeys 단위 테스트 (KAN-320) — 키 형식·소유키 식별(외부 URL 구분)·확장자 매핑. */
class ProfileImageKeysTest {

    @Test
    @DisplayName("objectKey — users/{userId}/profile/{uuid}.{ext} 형식")
    void objectKey_format() {
        assertThat(ProfileImageKeys.objectKey(3L, "image/jpeg")).matches("users/3/profile/[0-9a-fA-F\\-]{36}\\.jpg");
        assertThat(ProfileImageKeys.objectKey(3L, "image/png")).matches("users/3/profile/[0-9a-fA-F\\-]{36}\\.png");
        assertThat(ProfileImageKeys.objectKey(3L, "image/webp")).matches("users/3/profile/[0-9a-fA-F\\-]{36}\\.webp");
    }

    @Test
    @DisplayName("objectKey — 미허용 content-type → PROFILE_IMAGE_TYPE_UNSUPPORTED")
    void objectKey_unsupported() {
        assertThatThrownBy(() -> ProfileImageKeys.objectKey(3L, "image/gif"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROFILE_IMAGE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("isOwnedKey — 우리 키 형식만 true (외부 OAuth URL·null은 false)")
    void isOwnedKey() {
        String key = "users/3/profile/11111111-1111-1111-1111-111111111111.jpg";
        assertThat(ProfileImageKeys.isOwnedKey(key, 3L)).isTrue();
        assertThat(ProfileImageKeys.isOwnedKey(key)).isTrue();
        assertThat(ProfileImageKeys.isOwnedKey(key, 9L)).isFalse();                 // 타 유저 prefix
        assertThat(ProfileImageKeys.isOwnedKey("https://k.kakaocdn.net/abc.jpg")).isFalse(); // 외부 URL
        assertThat(ProfileImageKeys.isOwnedKey("users/3/profile/not-uuid.jpg")).isFalse();   // 패턴 위반
        assertThat(ProfileImageKeys.isOwnedKey(null)).isFalse();
    }
}
