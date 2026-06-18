package com.chunbaetour.domain.auth.profileimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountSeedFactory;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 프로필 업로드 롤백 보상 통합 테스트 (KAN-320 #3).
 * S3 업로드 성공 후 트랜잭션 롤백 시 방금 올린 객체가 afterRollback 훅으로 회수되는지 + 프로필이 미반영(롤백)되는지 검증.
 */
@SpringBootTest
class ProfileImageUploadRollbackCompensationTest extends AbstractIntegrationTest {

    @Autowired private ProfileImageService profileImageService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountSeedFactory accountSeedFactory;
    @MockitoBean private ProfileImageStorage imageStorage;

    private Long userId;

    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
    }

    @Test
    @DisplayName("업로드 후 트랜잭션 롤백 → 방금 올린 객체 보상 삭제 + 프로필 미반영")
    void uploadRollback_compensates() {
        Account account = accountSeedFactory.seed("rb@example.com", "Pa$$w0rd1!", "롤백유저", Role.USER, AccountStatus.ACTIVE);
        userId = account.getId();
        String uploadedKey = "users/" + userId + "/profile/11111111-1111-1111-1111-111111111111.jpg";

        given(imageStorage.upload(anyLong(), any())).willReturn(uploadedKey);
        // preview presign에서 non-EXTERNAL 예외 → 전파되어 트랜잭션 롤백 유발(부분 강등은 EXTERNAL만)
        given(imageStorage.presignedGetUrl(uploadedKey))
                .willThrow(new BusinessException(ErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> profileImageService.upload(userId, jpeg()))
                .isInstanceOf(BusinessException.class);

        // afterRollback 보상으로 방금 올린 객체 삭제
        verify(imageStorage).delete(uploadedKey);
        // 프로필은 롤백돼 미반영
        Account reloaded = accountRepository.findById(userId).orElseThrow();
        assertThat(reloaded.getProfileImageUrl()).isNull();
    }
}
