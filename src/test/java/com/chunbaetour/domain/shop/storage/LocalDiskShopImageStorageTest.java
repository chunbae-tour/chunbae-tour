package com.chunbaetour.domain.shop.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * LocalDiskShopImageStorage 단위 테스트 — 로컬 디스크 저장 + presign passthrough (E10).
 */
class LocalDiskShopImageStorageTest {

    @TempDir
    Path tempDir;

    private LocalDiskShopImageStorage storage() {
        return new LocalDiskShopImageStorage(tempDir.toString());
    }

    @Test
    @DisplayName("upload — 키 형식 반환 + 로컬 디스크에 파일 저장")
    void upload_savesToDisk_returnsKey() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});

        String key = storage().upload(10L, file);

        assertThat(key).matches("shops/10/[0-9a-fA-F\\-]{36}\\.jpg");
        assertThat(Files.exists(tempDir.resolve(key))).isTrue();
    }

    @Test
    @DisplayName("presignedGetUrl — 로컬은 실제 presign 없이 키 passthrough")
    void presignedGetUrl_passthrough() {
        assertThat(storage().presignedGetUrl("shops/10/x.jpg")).isEqualTo("shops/10/x.jpg");
    }
}
