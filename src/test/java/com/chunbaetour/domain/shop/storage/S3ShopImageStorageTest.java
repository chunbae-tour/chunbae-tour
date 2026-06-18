package com.chunbaetour.domain.shop.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.config.S3Properties;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3ShopImageStorage 단위 테스트 — S3Client 모킹.
 * PutObject 호출 + 객체 키 규칙(shops/{shopId}/{uuid}.{ext}) + 반환 키 + S3 오류 매핑 검증.
 */
@ExtendWith(MockitoExtension.class)
class S3ShopImageStorageTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner;

    private static final Long SHOP_ID = 10L;

    private S3ShopImageStorage storage() {
        S3Properties props = new S3Properties();
        props.setBucket("test-bucket");
        return new S3ShopImageStorage(s3Client, s3Presigner, props);
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
    }

    @Test
    @DisplayName("업로드 — PutObject 호출(버킷·키·content-type) + 키 반환(shops/{shopId}/{uuid}.jpg)")
    void upload_putsObject_andReturnsKey() {
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

        String key = storage().upload(SHOP_ID, jpeg());

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest req = captor.getValue();
        assertThat(req.bucket()).isEqualTo("test-bucket");
        assertThat(req.key()).isEqualTo(key);
        assertThat(req.contentType()).isEqualTo("image/jpeg");
        // 키 규칙: shops/{shopId}/{uuid}.jpg
        assertThat(key).matches("shops/10/[0-9a-fA-F\\-]{36}\\.jpg");
    }

    @Test
    @DisplayName("업로드 — S3 오류 → EXTERNAL_SERVICE_ERROR로 매핑")
    void upload_s3Error_mapsToExternalServiceError() {
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> storage().upload(SHOP_ID, jpeg()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    @Test
    @DisplayName("presignedGetUrl — 버킷·키로 presign 요청 후 URL 문자열 반환")
    void presignedGetUrl_returnsUrl() throws Exception {
        var presigned = org.mockito.Mockito.mock(
                software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest.class);
        given(presigned.url()).willReturn(java.net.URI.create("https://test-bucket.s3/shops/10/x.jpg?sig=abc").toURL());
        given(s3Presigner.presignGetObject(
                any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .willReturn(presigned);

        String url = storage().presignedGetUrl("shops/10/x.jpg");

        assertThat(url).isEqualTo("https://test-bucket.s3/shops/10/x.jpg?sig=abc");
        var captor = ArgumentCaptor.forClass(
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("shops/10/x.jpg");
    }

    @Test
    @DisplayName("presignedGetUrl — presign 중 SdkException → EXTERNAL_SERVICE_ERROR 매핑")
    void presignedGetUrl_s3Error_mapsToExternalServiceError() {
        given(s3Presigner.presignGetObject(
                any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .willThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> storage().presignedGetUrl("shops/10/x.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    @Test
    @DisplayName("listObjects — ListObjectsV2 페이지네이션(isTruncated) 끝까지 순회해 전 객체 수집 (KAN-319)")
    void listObjects_paginatesUntilNotTruncated() {
        java.time.Instant t1 = java.time.Instant.parse("2026-06-17T10:00:00Z");
        java.time.Instant t2 = java.time.Instant.parse("2026-06-17T11:00:00Z");
        software.amazon.awssdk.services.s3.model.ListObjectsV2Response page1 =
                software.amazon.awssdk.services.s3.model.ListObjectsV2Response.builder()
                        .contents(software.amazon.awssdk.services.s3.model.S3Object.builder()
                                .key("shops/10/a.jpg").lastModified(t1).build())
                        .isTruncated(true).nextContinuationToken("TOKEN2").build();
        software.amazon.awssdk.services.s3.model.ListObjectsV2Response page2 =
                software.amazon.awssdk.services.s3.model.ListObjectsV2Response.builder()
                        .contents(software.amazon.awssdk.services.s3.model.S3Object.builder()
                                .key("shops/20/b.jpg").lastModified(t2).build())
                        .isTruncated(false).build();
        // 첫 호출=page1(truncated) → 둘째 호출=page2(끝). 연속 반환으로 페이지네이션 루프 검증.
        given(s3Client.listObjectsV2(any(software.amazon.awssdk.services.s3.model.ListObjectsV2Request.class)))
                .willReturn(page1, page2);

        var result = storage().listObjects("shops/");

        assertThat(result).extracting(ShopImageObjectInfo::objectKey)
                .containsExactly("shops/10/a.jpg", "shops/20/b.jpg");
        assertThat(result).extracting(ShopImageObjectInfo::lastModified)
                .containsExactly(t1, t2);
        // 정확히 2회 호출(truncated 1 + 종료 1)
        verify(s3Client, org.mockito.Mockito.times(2))
                .listObjectsV2(any(software.amazon.awssdk.services.s3.model.ListObjectsV2Request.class));
    }

    @Test
    @DisplayName("listObjects — S3 오류 → EXTERNAL_SERVICE_ERROR 매핑(bucket/prefix 문맥 로깅 후) (KAN-319)")
    void listObjects_s3Error_mapsToExternalServiceError() {
        given(s3Client.listObjectsV2(any(software.amazon.awssdk.services.s3.model.ListObjectsV2Request.class)))
                .willThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> storage().listObjects("shops/"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR);
    }
}
