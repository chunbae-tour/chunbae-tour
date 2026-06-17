package com.chunbaetour.domain.community.free.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * LocalDiskPostImageStorage 단위 테스트 (KAN-317) — 로컬 디스크 저장 + presign passthrough.
 */
class LocalDiskPostImageStorageTest {

    @TempDir
    Path tempDir;

    private LocalDiskPostImageStorage storage() {
        return new LocalDiskPostImageStorage(tempDir.toString());
    }

    @Test
    @DisplayName("upload — posts/free/{userId}/{uuid}.jpg 키 반환 + 디스크 저장")
    void upload_savesToDisk() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});

        String key = storage().upload(7L, file);

        assertThat(key).matches("posts/free/7/[0-9a-fA-F\\-]{36}\\.jpg");
        assertThat(Files.exists(tempDir.resolve(key))).isTrue();
    }

    @Test
    @DisplayName("presignedGetUrl — 로컬은 키 passthrough")
    void presignedGetUrl_passthrough() {
        assertThat(storage().presignedGetUrl("posts/free/7/x.jpg")).isEqualTo("posts/free/7/x.jpg");
    }
}
