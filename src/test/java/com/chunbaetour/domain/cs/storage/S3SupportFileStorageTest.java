package com.chunbaetour.domain.cs.storage;

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
 * S3SupportFileStorage 단위 테스트 — S3Client 모킹 (KAN-310).
 * PutObject 호출 + 객체 키 규칙(support-rooms/{supportRoomId}/{uuid}.{ext}) + 반환 키 + S3 오류 매핑 검증.
 */
@ExtendWith(MockitoExtension.class)
class S3SupportFileStorageTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner;

    private static final Long ROOM_ID = 10L;

    private S3SupportFileStorage storage() {
        S3Properties props = new S3Properties();
        props.setBucket("test-bucket");
        return new S3SupportFileStorage(s3Client, s3Presigner, props);
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.4\n".getBytes());
    }

    @Test
    @DisplayName("업로드 — PutObject 호출(버킷·키·content-type) + 키 반환(support-rooms/{supportRoomId}/{uuid}.pdf)")
    void upload_putsObject_andReturnsKey() {
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

        String key = storage().upload(ROOM_ID, pdf());

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest req = captor.getValue();
        assertThat(req.bucket()).isEqualTo("test-bucket");
        assertThat(req.key()).isEqualTo(key);
        assertThat(req.contentType()).isEqualTo("application/pdf");
        assertThat(req.contentLength()).isEqualTo((long) "%PDF-1.4\n".getBytes().length);
        // 키 규칙: support-rooms/{supportRoomId}/{uuid}.pdf
        assertThat(key).matches("support-rooms/10/[0-9a-fA-F\\-]{36}\\.pdf");
    }

    @Test
    @DisplayName("업로드 — S3 오류 → EXTERNAL_SERVICE_ERROR로 매핑")
    void upload_s3Error_mapsToExternalServiceError() {
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> storage().upload(ROOM_ID, pdf()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    @Test
    @DisplayName("presignedGetUrl — 버킷·키로 presign 요청 후 URL 문자열 반환")
    void presignedGetUrl_returnsUrl() throws Exception {
        var presigned = org.mockito.Mockito.mock(
                software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest.class);
        given(presigned.url()).willReturn(java.net.URI.create("https://test-bucket.s3/support-rooms/10/x.pdf?sig=abc").toURL());
        given(s3Presigner.presignGetObject(
                any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .willReturn(presigned);

        String url = storage().presignedGetUrl("support-rooms/10/x.pdf");

        assertThat(url).isEqualTo("https://test-bucket.s3/support-rooms/10/x.pdf?sig=abc");
        var captor = ArgumentCaptor.forClass(
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("support-rooms/10/x.pdf");
    }

    @Test
    @DisplayName("presignedGetUrl — presign 중 SdkException → EXTERNAL_SERVICE_ERROR 매핑")
    void presignedGetUrl_s3Error_mapsToExternalServiceError() {
        given(s3Presigner.presignGetObject(
                any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .willThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> storage().presignedGetUrl("support-rooms/10/x.pdf"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR);
    }
}
