package com.chunbaetour.domain.community.free.storage;

import com.chunbaetour.domain.common.config.S3Properties;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * 운영 S3 자유게시판 이미지 저장소 (KAN-317, {@code @Profile("prod")}).
 *
 * <p>검증 완료 파일을 {@code posts/free/{userId}/{uuid}.{ext}} 키로 PutObject 한다. 버킷은 완전 비공개라
 * 조회 URL이 아닌 <b>객체 키</b>를 반환한다(조회는 presigned GET). 공통 S3 인프라(S3Config/S3Properties) 위에 올린다.
 */
@Slf4j
@Component
@Profile("prod")
public class S3PostImageStorage implements PostImageStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public S3PostImageStorage(S3Client s3Client, S3Presigner s3Presigner, S3Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public String upload(Long userId, MultipartFile file) {
        String key = PostImageKeys.objectKey(userId, file.getContentType());
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | SdkException e) {
            // 외부 서비스(S3) 오류·IO 실패만 503으로 매핑. RuntimeException 전체를 잡지 않아 내부 버그(NPE 등)는
            // 그대로 전파돼 500으로 드러난다(모니터링서 외부 장애와 구분). 키 미저장(고아 객체 없음).
            log.error("S3 자유게시판 이미지 업로드 실패: bucket={}, key={}", properties.getBucket(), key, e);
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
            log.error("S3 presigned GET URL 발급 실패: bucket={}, key={}", properties.getBucket(), key, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    @Override
    public void delete(String key) {
        // best-effort — 삭제 실패해도 throw 안 함(고아는 스케줄러가 회수). S3 DeleteObject는 객체 부재 시에도 성공.
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
        } catch (SdkException e) {
            log.error("S3 자유게시판 이미지 삭제 실패(고아로 잔존, 스케줄러 회수 대상): bucket={}, key={}",
                    properties.getBucket(), key, e);
        }
    }

    @Override
    public List<PostImageObjectInfo> listObjects(String prefix) {
        // ListObjectsV2 페이지네이션 — 1000개 초과도 토큰으로 끝까지 순회. LastModified는 UTC Instant.
        List<PostImageObjectInfo> result = new ArrayList<>();
        ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                .bucket(properties.getBucket())
                .prefix(prefix);
        ListObjectsV2Response response;
        String continuationToken = null;
        try {
            do {
                response = s3Client.listObjectsV2(requestBuilder.continuationToken(continuationToken).build());
                for (S3Object obj : response.contents()) {
                    result.add(new PostImageObjectInfo(obj.key(), obj.lastModified()));
                }
                continuationToken = response.nextContinuationToken();
            } while (Boolean.TRUE.equals(response.isTruncated()));
        } catch (SdkException e) {
            // S3 일시장애·권한 문제 — bucket/prefix 문맥 ERROR 로깅 후 EXTERNAL_SERVICE_ERROR로 매핑(스케줄러가 잡아 이번 주기 skip).
            log.error("S3 자유게시판 이미지 객체 나열 실패: bucket={}, prefix={}", properties.getBucket(), prefix, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        return result;
    }
}
