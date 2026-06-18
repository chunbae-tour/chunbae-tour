package com.chunbaetour.domain.auth.profileimage;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬/테스트용 프로필 이미지 저장소 (KAN-320, {@code @Profile("!prod")}). S3와 동일 키 형식, presign passthrough.
 * 저장 위치는 {@code profile.image.local-dir}(미설정 시 {@code java.io.tmpdir}/chunbae-uploads).
 */
@Slf4j
@Component
@Profile("!prod")
public class LocalDiskProfileImageStorage implements ProfileImageStorage {

    private final Path baseDir;

    public LocalDiskProfileImageStorage(@Value("${profile.image.local-dir:}") String configuredDir) {
        String dir = (configuredDir == null || configuredDir.isBlank())
                ? System.getProperty("java.io.tmpdir") + "/chunbae-uploads"
                : configuredDir;
        this.baseDir = Path.of(dir);
    }

    @Override
    public String upload(Long userId, MultipartFile file) {
        String key = ProfileImageKeys.objectKey(userId, file.getContentType());
        Path target = baseDir.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        return key;
    }

    @Override
    public String presignedGetUrl(String key) {
        return key;
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(baseDir.resolve(key));
        } catch (IOException e) {
            log.warn("로컬 프로필 이미지 삭제 실패(잔존): key={}", key, e);
        }
    }
}
