package com.chunbaetour.domain.community.free.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.community.free.dto.PostImageUploadResponse;
import com.chunbaetour.domain.community.free.storage.PostImageStorage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * PostImageService 단위 테스트 (KAN-317) — 업로드 검증·키 소유권/개수 검증·presign 변환.
 */
@ExtendWith(MockitoExtension.class)
class PostImageServiceTest {

    @Mock private PostImageStorage imageStorage;
    @InjectMocks private PostImageService postImageService;

    private static final Long USER_ID = 7L;

    /** JPEG 매직바이트(FF D8 FF)로 시작하는 더미. */
    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "x.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
    }

    // ── 업로드 검증 ──────────────────────────────────────────────

    @Test
    @DisplayName("업로드 — 검증 통과 후 storage 위임 → 키 + 미리보기 URL 반환")
    void upload_returnsKeyAndPreview() {
        given(imageStorage.upload(eq(USER_ID), any())).willReturn("posts/free/7/uuid.jpg");
        given(imageStorage.presignedGetUrl("posts/free/7/uuid.jpg")).willReturn("https://signed/preview");

        PostImageUploadResponse res = postImageService.upload(USER_ID, jpeg());

        assertThat(res.objectKey()).isEqualTo("posts/free/7/uuid.jpg");
        assertThat(res.previewUrl()).isEqualTo("https://signed/preview");
    }

    @Test
    @DisplayName("업로드 — 빈 파일 → COMMUNITY_016")
    void upload_empty_throws() {
        MockMultipartFile empty = new MockMultipartFile("file", "e.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> postImageService.upload(USER_ID, empty))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_IMAGE_FILE_EMPTY);
    }

    @Test
    @DisplayName("업로드 — 10MB 초과 → COMMUNITY_015")
    void upload_tooLarge_throws() {
        MockMultipartFile big = new MockMultipartFile("file", "b.jpg", "image/jpeg", new byte[11 * 1024 * 1024]);
        assertThatThrownBy(() -> postImageService.upload(USER_ID, big))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_IMAGE_FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("업로드 — 허용 안 되는 타입(gif) → COMMUNITY_014")
    void upload_badType_throws() {
        MockMultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{'G', 'I', 'F', '8'});
        assertThatThrownBy(() -> postImageService.upload(USER_ID, gif))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_IMAGE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("업로드 — declared=jpeg지만 매직바이트 불일치(위장) → COMMUNITY_014")
    void upload_magicMismatch_throws() {
        MockMultipartFile fake = new MockMultipartFile("file", "evil.jpg", "image/jpeg", new byte[]{'M', 'Z', 0, 0});
        assertThatThrownBy(() -> postImageService.upload(USER_ID, fake))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_IMAGE_TYPE_UNSUPPORTED);
    }

    // ── 키 소유권/개수 검증 ──────────────────────────────────────

    @Test
    @DisplayName("validateOwnedKeys — null/빈 목록 통과")
    void validateOwnedKeys_empty_ok() {
        postImageService.validateOwnedKeys(USER_ID, null);
        postImageService.validateOwnedKeys(USER_ID, List.of());
    }

    @Test
    @DisplayName("validateOwnedKeys — 5장 초과 → COMMUNITY_013")
    void validateOwnedKeys_tooMany_throws() {
        List<String> six = List.of(
                "posts/free/7/a.jpg", "posts/free/7/b.jpg", "posts/free/7/c.jpg",
                "posts/free/7/d.jpg", "posts/free/7/e.jpg", "posts/free/7/f.jpg");
        assertThatThrownBy(() -> postImageService.validateOwnedKeys(USER_ID, six))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_IMAGE_COUNT_EXCEEDED);
    }

    @Test
    @DisplayName("validateOwnedKeys — 타인 prefix 키(IDOR) → INVALID_REQUEST")
    void validateOwnedKeys_foreignKey_throws() {
        List<String> foreign = List.of("posts/free/999/other.jpg"); // userId 7이 아닌 키
        assertThatThrownBy(() -> postImageService.validateOwnedKeys(USER_ID, foreign))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("validateOwnedKeys — 본인 prefix 5장 이하 통과")
    void validateOwnedKeys_ownKeys_ok() {
        postImageService.validateOwnedKeys(USER_ID, List.of("posts/free/7/a.jpg", "posts/free/7/b.jpg"));
    }

    // ── presign 변환 ────────────────────────────────────────────

    @Test
    @DisplayName("presignAll — 키 목록을 presigned URL로 변환, presign 실패한 건만 건너뜀(graceful degrade)")
    void presignAll_gracefulDegrade() {
        given(imageStorage.presignedGetUrl("posts/free/7/a.jpg")).willReturn("https://signed/a");
        given(imageStorage.presignedGetUrl("posts/free/7/b.jpg"))
                .willThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR));
        given(imageStorage.presignedGetUrl("posts/free/7/c.jpg")).willReturn("https://signed/c");

        List<String> urls = postImageService.presignAll(
                List.of("posts/free/7/a.jpg", "posts/free/7/b.jpg", "posts/free/7/c.jpg"));

        // b는 presign 실패로 제외 — 나머지는 정상 변환
        assertThat(urls).containsExactly("https://signed/a", "https://signed/c");
    }

    @Test
    @DisplayName("presignAll — null/빈 목록 → 빈 목록")
    void presignAll_empty() {
        assertThat(postImageService.presignAll(null)).isEmpty();
        assertThat(postImageService.presignAll(List.of())).isEmpty();
    }
}
