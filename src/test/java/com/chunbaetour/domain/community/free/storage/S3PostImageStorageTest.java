package com.chunbaetour.domain.community.free.storage;

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
 * S3PostImageStorage 단위 테스트 (KAN-317) — PutObject·키 규칙·반환 키·S3 오류 매핑.
 */
@ExtendWith(MockitoExtension.class)
class S3PostImageStorageTest {

    @Mock private S3Client s3Client;
    @Mock private software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner;

    private static final Long USER_ID = 7L;

    private S3PostImageStorage storage() {
        S3Properties props = new S3Properties();
        props.setBucket("test-bucket");
        return new S3PostImageStorage(s3Client, s3Presigner, props);
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "x.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
    }

    @Test
    @DisplayName("업로드 — PutObject(버킷·키·content-type) + 키 반환(posts/free/{userId}/{uuid}.jpg)")
    void upload_putsObject_andReturnsKey() {
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

        String key = storage().upload(USER_ID, jpeg());

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo(key);
        assertThat(captor.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(key).matches("posts/free/7/[0-9a-fA-F\\-]{36}\\.jpg");
    }

    @Test
    @DisplayName("업로드 — S3 오류 → EXTERNAL_SERVICE_ERROR 매핑")
    void upload_s3Error_mapsToExternalServiceError() {
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> storage().upload(USER_ID, jpeg()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    @Test
    @DisplayName("presignedGetUrl — presign 중 SdkException → EXTERNAL_SERVICE_ERROR 매핑")
    void presignedGetUrl_s3Error_mapsToExternalServiceError() {
        given(s3Presigner.presignGetObject(
                any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .willThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> storage().presignedGetUrl("posts/free/7/x.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR);
    }
}
