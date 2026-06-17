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

    @Test
    @DisplayName("listObjects — prefix 아래 파일을 키(forward-slash 정규화)로 나열, 미존재 prefix는 빈 목록")
    void listObjects_walksPrefix() {
        LocalDiskShopImageStorage storage = storage();
        String k1 = storage.upload(10L, jpeg());
        String k2 = storage.upload(20L, jpeg());

        // shops/ 전체 — 업로드한 2개 키 포함, 키는 '/' 정규화
        assertThat(storage.listObjects("shops/"))
                .extracting(ShopImageObjectInfo::objectKey)
                .containsExactlyInAnyOrder(k1, k2);
        assertThat(storage.listObjects("shops/")).allSatisfy(o -> {
            assertThat(o.objectKey()).doesNotContain("\\");
            assertThat(o.lastModified()).isNotNull();
        });

        // 특정 가게 prefix로 좁히면 그 가게 객체만
        assertThat(storage.listObjects("shops/10/"))
                .extracting(ShopImageObjectInfo::objectKey)
                .containsExactly(k1);

        // 존재하지 않는 prefix → 빈 목록(throw 없음)
        assertThat(storage.listObjects("shops/999/")).isEmpty();
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "x.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
    }
}
