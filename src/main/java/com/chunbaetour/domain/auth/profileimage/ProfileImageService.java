package com.chunbaetour.domain.auth.profileimage;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 이미지 서비스 (KAN-320).
 *
 * <p>유저(Account)는 업로드 시점에 항상 존재하므로 <b>1단계 업로드</b>: 검증 → S3 업로드 → {@code Account.profileImageUrl}에
 * 객체 키 즉시 세팅. 유저당 1장이라 재업로드 시 교체하며, 직전 값이 "우리 키"면 옛 객체를 afterCommit best-effort 삭제한다
 * (외부 OAuth URL이면 미삭제 — 이중 출처).
 *
 * <p>[조회 변환] {@link #toDisplayUrl}는 저장값을 표시용으로 변환한다 — 우리 키면 presigned URL, 외부 http(s) URL이면
 * 그대로(passthrough), null이면 null. 마이페이지뿐 아니라 커뮤니티/채팅/리뷰/관리자 등 프로필이 노출되는 모든 응답이 재사용한다.
 *
 * <p>[보안] content-type 화이트리스트 + magic-byte 시그니처(JPEG/PNG/WebP)로 위장 업로드 차단(shop E10 패턴 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final AccountRepository accountRepository;
    private final ProfileImageStorage imageStorage;

    /**
     * 프로필 이미지 업로드(1단계). 검증 후 S3 업로드 → 본인 Account의 profileImageUrl을 객체 키로 교체.
     * 교체로 밀려난 직전 값이 "우리 키"면 커밋 후 옛 객체 정리(외부 URL은 미삭제).
     */
    @Transactional
    public ProfileImageUploadResponse upload(Long userId, MultipartFile file) {
        validateFile(file);
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));

        String objectKey = imageStorage.upload(userId, file);
        String previous = account.changeProfileImage(objectKey);

        // 직전 값이 우리 업로드 키였다면 교체로 고아가 되므로 커밋 후 정리. 외부 OAuth URL이면 우리 소유 아니라 건드리지 않음.
        if (ProfileImageKeys.isOwnedKey(previous, userId)) {
            deleteObjectAfterCommit(previous);
        }

        // preview presign은 부가 — 실패해도 업로드(키 세팅)는 유효. 부분 실패 강등(키는 응답).
        String previewUrl = null;
        try {
            previewUrl = imageStorage.presignedGetUrl(objectKey);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.EXTERNAL_SERVICE_ERROR) {
                throw e;
            }
            log.warn("프로필 업로드 성공했지만 preview presign 실패 — 키는 반영. objectKey={}", objectKey, e);
        }
        return new ProfileImageUploadResponse(objectKey, previewUrl);
    }

    /**
     * 저장값 → 표시용 URL. 우리 업로드 키면 presigned URL, 외부 http(s) URL이면 그대로, null/blank면 null.
     * presign 실패(외부 의존)는 null로 강등 — 프로필 한 장 때문에 응답 전체가 503이 되지 않게(graceful degrade).
     */
    public String toDisplayUrl(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!ProfileImageKeys.isOwnedKey(stored)) {
            return stored; // 외부 OAuth URL 등 — 그대로 노출
        }
        try {
            return imageStorage.presignedGetUrl(stored);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.EXTERNAL_SERVICE_ERROR) {
                throw e;
            }
            log.warn("프로필 presign 실패 — null로 강등. key={}", stored, e);
            return null;
        }
    }

    private void deleteObjectAfterCommit(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            imageStorage.delete(objectKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageStorage.delete(objectKey);
            }
        });
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_TYPE_UNSUPPORTED);
        }
        if (!isAllowedImageSignature(file)) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_TYPE_UNSUPPORTED);
        }
    }

    private static boolean isAllowedImageSignature(MultipartFile file) {
        byte[] h;
        try {
            h = file.getInputStream().readNBytes(12);
        } catch (IOException e) {
            return false;
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> isJpeg(h);
            case "image/png" -> isPng(h);
            case "image/webp" -> isWebp(h);
            default -> false;
        };
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A;
    }

    private static boolean isWebp(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
