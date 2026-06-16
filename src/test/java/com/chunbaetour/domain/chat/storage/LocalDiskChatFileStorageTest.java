package com.chunbaetour.domain.chat.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * LocalDiskChatFileStorage 단위 테스트 — 로컬 디스크 저장 + presign passthrough (KAN-309).
 */
class LocalDiskChatFileStorageTest {

    @TempDir
    Path tempDir;

    private LocalDiskChatFileStorage storage() {
        return new LocalDiskChatFileStorage(tempDir.toString());
    }

    @Test
    @DisplayName("upload — 키 형식 반환 + 로컬 디스크에 파일 저장")
    void upload_savesToDisk_returnsKey() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "%PDF-1.4\n".getBytes());

        String key = storage().upload(10L, file);

        assertThat(key).matches("chat-rooms/10/[0-9a-fA-F\\-]{36}\\.pdf");
        assertThat(Files.exists(tempDir.resolve(key))).isTrue();
    }

    @Test
    @DisplayName("presignedGetUrl — 로컬은 실제 presign 없이 키 passthrough")
    void presignedGetUrl_passthrough() {
        assertThat(storage().presignedGetUrl("chat-rooms/10/x.pdf")).isEqualTo("chat-rooms/10/x.pdf");
    }
}
