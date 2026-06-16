package com.chunbaetour.domain.cs.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * LocalDiskSupportFileStorage 단위 테스트 — 로컬 디스크 저장 + presign passthrough (KAN-310).
 */
class LocalDiskSupportFileStorageTest {

    @TempDir
    Path tempDir;

    private LocalDiskSupportFileStorage storage() {
        return new LocalDiskSupportFileStorage(tempDir.toString());
    }

    @Test
    @DisplayName("upload — 키 형식 반환 + 로컬 디스크에 파일 저장")
    void upload_savesToDisk_returnsKey() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "%PDF-1.4\n".getBytes());

        String key = storage().upload(10L, file);

        assertThat(key).matches("support-rooms/10/[0-9a-fA-F\\-]{36}\\.pdf");
        assertThat(Files.exists(tempDir.resolve(key))).isTrue();
    }

    @Test
    @DisplayName("presignedGetUrl — 로컬은 실제 presign 없이 키 passthrough")
    void presignedGetUrl_passthrough() {
        assertThat(storage().presignedGetUrl("support-rooms/10/x.pdf")).isEqualTo("support-rooms/10/x.pdf");
    }
}
