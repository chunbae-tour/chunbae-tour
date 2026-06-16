package com.chunbaetour.domain.shop.storage;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬/테스트용 이미지 저장소 (E10, {@code @Profile("!prod")}).
 *
 * <p>운영(S3) 외 환경에서 업로드 흐름을 실제로 확인할 수 있도록 <b>로컬 디스크에 저장</b>한다(throw 안 함).
 * S3와 동일한 키 형식({@code shops/{shopId}/{uuid}.{ext}})을 반환해 후속 조회/연동 코드가 환경 무관하게 동작한다.
 * 저장 위치는 {@code shop.image.local-dir}(미설정 시 {@code java.io.tmpdir}/chunbae-uploads).
 */
@Component
@Profile("!prod")
public class LocalDiskShopImageStorage implements ShopImageStorage {

    private final Path baseDir;

    public LocalDiskShopImageStorage(@Value("${shop.image.local-dir:}") String configuredDir) {
        String dir = (configuredDir == null || configuredDir.isBlank())
                ? System.getProperty("java.io.tmpdir") + "/chunbae-uploads"
                : configuredDir;
        this.baseDir = Path.of(dir);
    }

    @Override
    public String upload(Long shopId, MultipartFile file) {
        String key = ShopImageKeys.objectKey(shopId, file.getContentType());
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

    /**
     * 로컬은 실제 presign이 없으므로 키를 그대로 반환(passthrough). 개발자는 키로 로컬 파일을 직접 확인.
     * 운영(S3)에서만 만료 있는 presigned URL이 발급된다.
     */
    @Override
    public String presignedGetUrl(String key) {
        return key;
    }
}
