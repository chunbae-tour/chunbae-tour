package com.chunbaetour.domain.auth.profileimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/** ProfileImageService 단위 테스트 (KAN-320) — 업로드 검증·교체 옛키 삭제·toDisplayUrl 분기. */
@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private ProfileImageStorage imageStorage;
    @InjectMocks private ProfileImageService profileImageService;

    private static final Long USER_ID = 3L;
    private static final String OWN_KEY = "users/3/profile/11111111-1111-1111-1111-111111111111.jpg";

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
    }

    @Test
    @DisplayName("업로드 — 검증 통과 → 키 세팅 + 응답(키+preview)")
    void upload_setsKey() {
        Account account = org.mockito.Mockito.mock(Account.class);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        given(imageStorage.upload(eq(USER_ID), any())).willReturn(OWN_KEY);
        given(account.changeProfileImage(OWN_KEY)).willReturn(null); // 직전 값 없음
        given(imageStorage.presignedGetUrl(OWN_KEY)).willReturn("https://signed/me");

        var res = profileImageService.upload(USER_ID, jpeg());

        // raw 키는 응답에 없다(E10) — previewUrl만 노출, 키 세팅은 changeProfileImage로 검증
        assertThat(res.previewUrl()).isEqualTo("https://signed/me");
        verify(account).changeProfileImage(OWN_KEY);
    }

    @Test
    @DisplayName("업로드 — 직전 값이 우리 키면 옛 객체 삭제(교체 고아 정리)")
    void upload_replacesOwnedOldKey_deletesOld() {
        String oldKey = "users/3/profile/22222222-2222-2222-2222-222222222222.png";
        Account account = org.mockito.Mockito.mock(Account.class);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        given(imageStorage.upload(eq(USER_ID), any())).willReturn(OWN_KEY);
        given(account.changeProfileImage(OWN_KEY)).willReturn(oldKey);
        given(imageStorage.presignedGetUrl(OWN_KEY)).willReturn("https://signed/me");

        profileImageService.upload(USER_ID, jpeg());

        // 트랜잭션 동기화 없음(단위) → 즉시 삭제 폴백 경로로 옛 키 삭제
        verify(imageStorage).delete(oldKey);
    }

    @Test
    @DisplayName("업로드 — 직전 값이 외부 OAuth URL이면 삭제 안 함")
    void upload_externalOldUrl_notDeleted() {
        Account account = org.mockito.Mockito.mock(Account.class);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        given(imageStorage.upload(eq(USER_ID), any())).willReturn(OWN_KEY);
        given(account.changeProfileImage(OWN_KEY)).willReturn("https://k.kakaocdn.net/old.jpg");
        given(imageStorage.presignedGetUrl(OWN_KEY)).willReturn("https://signed/me");

        profileImageService.upload(USER_ID, jpeg());

        verify(imageStorage, never()).delete(any());
    }

    @Test
    @DisplayName("업로드 — 빈/대용량/형식 위반 에러코드")
    void upload_validation() {
        // validateFile이 findById보다 먼저 실행돼 검증 실패 시 계정 조회·저장소 미도달
        assertThatThrownBy(() -> profileImageService.upload(USER_ID,
                new MockMultipartFile("file", "e.jpg", "image/jpeg", new byte[0])))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PROFILE_IMAGE_FILE_EMPTY);
        assertThatThrownBy(() -> profileImageService.upload(USER_ID,
                new MockMultipartFile("file", "b.jpg", "image/jpeg", new byte[6 * 1024 * 1024])))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PROFILE_IMAGE_FILE_TOO_LARGE);
        assertThatThrownBy(() -> profileImageService.upload(USER_ID,
                new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{'G', 'I', 'F'})))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PROFILE_IMAGE_TYPE_UNSUPPORTED);
        // declared=image/jpeg지만 실제 바이트는 MZ(PE 실행파일) → magic-byte 불일치로 위장 차단
        assertThatThrownBy(() -> profileImageService.upload(USER_ID,
                new MockMultipartFile("file", "evil.jpg", "image/jpeg", new byte[]{'M', 'Z', 0, 0})))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PROFILE_IMAGE_TYPE_UNSUPPORTED);
    }

    @Test
    @DisplayName("업로드 — preview presign이 EXTERNAL 아닌 에러면 강등 없이 전파")
    void upload_presignNonExternal_propagates() {
        Account account = org.mockito.Mockito.mock(Account.class);
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        given(imageStorage.upload(eq(USER_ID), any())).willReturn(OWN_KEY);
        given(account.changeProfileImage(OWN_KEY)).willReturn(null);
        given(imageStorage.presignedGetUrl(OWN_KEY)).willThrow(new BusinessException(ErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> profileImageService.upload(USER_ID, jpeg()))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("cleanupReplacedKey — 옛 값이 우리 키면 삭제, 외부/null이면 미삭제")
    void cleanupReplacedKey_branches() {
        // 우리 키 → 삭제(트랜잭션 동기화 없음 → 즉시 삭제 폴백)
        profileImageService.cleanupReplacedKey(OWN_KEY, USER_ID);
        verify(imageStorage).delete(OWN_KEY);

        // 외부 URL / null → 미삭제
        profileImageService.cleanupReplacedKey("https://k.kakaocdn.net/old.jpg", USER_ID);
        profileImageService.cleanupReplacedKey(null, USER_ID);
        verify(imageStorage, never()).delete("https://k.kakaocdn.net/old.jpg");
    }

    @Test
    @DisplayName("toDisplayUrl — 우리 키→presign / 외부 URL→passthrough / null→null / presign실패→null")
    void toDisplayUrl_branches() {
        given(imageStorage.presignedGetUrl(OWN_KEY)).willReturn("https://signed/x");
        assertThat(profileImageService.toDisplayUrl(OWN_KEY)).isEqualTo("https://signed/x");
        assertThat(profileImageService.toDisplayUrl("https://k.kakaocdn.net/p.jpg")).isEqualTo("https://k.kakaocdn.net/p.jpg");
        assertThat(profileImageService.toDisplayUrl(null)).isNull();
        assertThat(profileImageService.toDisplayUrl("  ")).isNull();
    }

    @Test
    @DisplayName("toDisplayUrl — 우리 키 presign 실패(EXTERNAL) → null 강등")
    void toDisplayUrl_presignFail_null() {
        given(imageStorage.presignedGetUrl(OWN_KEY)).willThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR));
        assertThat(profileImageService.toDisplayUrl(OWN_KEY)).isNull();
    }
}
