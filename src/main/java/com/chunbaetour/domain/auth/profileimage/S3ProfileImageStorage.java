package com.chunbaetour.domain.auth.profileimage;

import com.chunbaetour.domain.common.config.S3Properties;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * 운영 S3 프로필 이미지 저장소 (KAN-320, {@code @Profile("prod")}). 공통 S3 인프라(S3Config/S3Properties) 사용.
 */
@Slf4j
@Component
@Profile("prod")
public class S3ProfileImageStorage implements ProfileImageStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public S3ProfileImageStorage(S3Client s3Client, S3Presigner s3Presigner, S3Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public String upload(Long userId, MultipartFile file) {
        String key = ProfileImageKeys.objectKey(userId, file.getContentType());
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();
        try {
            // RequestBody.fromInputStream은 스트림을 자동으로 닫지 않음(SDK v2) → try-with-resources로 FD 누수 방지.
            try (var in = file.getInputStream()) {
                s3Client.putObject(request, RequestBody.fromInputStream(in, file.getSize()));
            }
        } catch (IOException | SdkException e) {
            log.error("S3 프로필 이미지 업로드 실패: bucket={}, key={}", properties.getBucket(), key, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        return key;
    }

    @Override
    public String presignedGetUrl(String key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(properties.getPresignExpiry())
                .getObjectRequest(getRequest)
                .build();
        try {
            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (SdkException e) {
            log.error("S3 프로필 presigned GET URL 발급 실패: bucket={}, key={}", properties.getBucket(), key, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
        } catch (SdkException e) {
            log.error("S3 프로필 이미지 삭제 실패(고아로 잔존): bucket={}, key={}", properties.getBucket(), key, e);
        }
    }
}
