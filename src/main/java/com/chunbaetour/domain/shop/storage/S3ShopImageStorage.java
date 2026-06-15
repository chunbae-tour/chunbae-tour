package com.chunbaetour.domain.shop.storage;

import com.chunbaetour.domain.common.config.S3Properties;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 운영 S3 이미지 저장소 (E10, {@code @Profile("prod")}).
 *
 * <p>검증 완료 파일을 {@code shops/{shopId}/{uuid}.{ext}} 키로 PutObject 한다. 버킷은 완전 비공개라
 * 조회 URL이 아닌 <b>객체 키</b>를 반환한다(조회는 presigned GET — PR3).
 * 자격증명은 {@link S3Client}(DefaultCredentialsProvider, ECS task-role 자동) — 액세스키 없음.
 */
@Component
@Profile("prod")
public class S3ShopImageStorage implements ShopImageStorage {

    private final S3Client s3Client;
    private final S3Properties properties;

    public S3ShopImageStorage(S3Client s3Client, S3Properties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public String upload(Long shopId, MultipartFile file) {
        String key = ShopImageKeys.objectKey(shopId, file.getContentType());
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | RuntimeException e) {
            // 네트워크/권한/IO 실패 — 외부 서비스 오류로 매핑(컨트롤러 5xx). 키는 저장하지 않음(고아 객체 없음).
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        return key;
    }
}
