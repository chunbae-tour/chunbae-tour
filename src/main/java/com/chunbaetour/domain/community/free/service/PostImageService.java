package com.chunbaetour.domain.community.free.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.community.free.dto.PostImageUploadResponse;
import com.chunbaetour.domain.community.free.storage.PostImageKeys;
import com.chunbaetour.domain.community.free.storage.PostImageStorage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 자유게시판 이미지 서비스 (KAN-317).
 *
 * <p>업로드는 글 작성 전(postId 없음) 화면에서 일어나므로 <b>객체 키만 반환</b>한다. 클라이언트가 그 키를 글
 * 작성/수정 요청의 {@code imageUrls}에 담아 보내면 그때 영속화한다(키 저장). 조회 시 키 → presigned GET URL로 변환한다.
 *
 * <p>[보안 경계] {@code MultipartFile.getContentType()}은 user-supplied라 .exe를 image/jpeg로 위장 가능 →
 * declared content-type 화이트리스트 + magic-byte 시그니처(JPEG/PNG/WebP)를 함께 검증한다(경량, Tika 미사용 — shop E10 패턴 참고).
 *
 * <p>[책임 분리] 업로드 검증·저장은 본 서비스가, 키의 영속화(개수/소유권 검증 포함)는 {@code FreePostService}가
 * 본 서비스의 헬퍼({@link #validateOwnedKeys}, {@link #presignAll})를 호출해 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostImageService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB
    private static final int MAX_IMAGE_COUNT = 5;
    /** "image/jpg"는 비표준 MIME — 브라우저는 .jpg/.jpeg 모두 image/jpeg로 전송하므로 포함 불필요 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final PostImageStorage imageStorage;

    /**
     * 이미지 업로드(글 작성 전 단계). 파일 유효성 검사(크기·content-type·magic-byte) 후 저장소에 위임하고 객체 키를 반환한다.
     * 글이 아직 없으므로 키 영속화는 하지 않는다 — 클라가 작성/수정 요청에 키를 실어 보내면 그때 저장된다.
     */
    public PostImageUploadResponse upload(Long userId, MultipartFile file) {
        validateFile(file);
        String objectKey = imageStorage.upload(userId, file);
        // 업로드 직후 미리보기용 presigned URL도 함께 — 작성 화면에서 글 저장 전 바로 렌더 가능(비공개 버킷이라 키만으론 못 봄)
        return new PostImageUploadResponse(objectKey, imageStorage.presignedGetUrl(objectKey));
    }

    /**
     * 글 작성/수정 시 imageUrls(객체 키) 영속화 전 검증 — 개수(≤5) + 각 키의 작성자 소유 prefix.
     * null/빈 목록은 통과(이미지 없음). 5장 초과는 COMMUNITY_013, 타인/임의 키는 INVALID_REQUEST(IDOR 차단).
     */
    public void validateOwnedKeys(Long userId, List<String> imageKeys) {
        if (imageKeys == null || imageKeys.isEmpty()) {
            return;
        }
        if (imageKeys.size() > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.POST_IMAGE_COUNT_EXCEEDED);
        }
        for (String key : imageKeys) {
            // 자기 prefix(posts/free/{userId}/) 아닌 키(타인/임의 객체)는 거부 — 저장 후 presign 노출 차단(IDOR)
            if (!PostImageKeys.belongsToUser(key, userId)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }

    /**
     * 저장된 객체 키 목록 → presigned GET URL 목록으로 변환(조회 응답용).
     * presign 실패(외부 의존)는 해당 이미지만 건너뛴다(graceful degrade — 한 장 때문에 글 조회 전체가 503이 되지 않게).
     * null/빈 목록은 빈 목록 반환.
     */
    public List<String> presignAll(List<String> imageKeys) {
        if (imageKeys == null || imageKeys.isEmpty()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>(imageKeys.size());
        for (String key : imageKeys) {
            try {
                urls.add(imageStorage.presignedGetUrl(key));
            } catch (BusinessException e) {
                // 외부 의존(presign) 실패만 부분 실패로 격하 — 그 외 내부 결함은 전파해 가시화.
                if (e.getErrorCode() != ErrorCode.EXTERNAL_SERVICE_ERROR) {
                    throw e;
                }
                log.warn("presign 실패로 이미지 건너뜀. key={}", key, e);
            }
        }
        return urls;
    }

    /**
     * 주어진 키들의 S3 객체 삭제를 현재 트랜잭션 커밋 직후(afterCommit)로 지연한다(KAN-317).
     * 글 수정(교체/제거된 옛 키)·삭제 시 호출 — 커밋 전 즉시 삭제하면 이후 롤백 시 객체만 사라져 정합성이 깨지므로
     * 커밋이 확정된 뒤에만 best-effort 삭제한다. 동기화가 없으면(트랜잭션 밖) 즉시 삭제로 폴백. null/빈 목록은 no-op.
     */
    public void deleteAllAfterCommit(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<String> snapshot = List.copyOf(keys);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            snapshot.forEach(imageStorage::delete);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                snapshot.forEach(imageStorage::delete);
            }
        });
    }

    private void validateFile(MultipartFile file) {
        // Spring @RequestParam 누락 시 서비스 도달 전 400 반환 — null은 실제 발생하지 않음
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.POST_IMAGE_FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.POST_IMAGE_FILE_TOO_LARGE);
        }
        // 1차: declared Content-Type 화이트리스트. null 방어: per-part 헤더 없으면 getContentType()=null.
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.POST_IMAGE_TYPE_UNSUPPORTED);
        }
        // 2차: magic-byte 시그니처 — declared가 image/jpeg여도 실제 바이트가 이미지가 아니면 거부(.exe 위장 차단).
        if (!isAllowedImageSignature(file)) {
            throw new BusinessException(ErrorCode.POST_IMAGE_TYPE_UNSUPPORTED);
        }
    }

    /**
     * 매직바이트가 declared content-type과 <b>같은 포맷</b>인지 검사.
     * "허용 포맷 중 하나"가 아니라 "선언값과 일치"를 봐야 declared=png/실제=jpeg 같은 불일치를 막는다.
     */
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

    // JPEG: FF D8 FF
    private static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    // PNG: 89 50 4E 47 0D 0A 1A 0A
    private static boolean isPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A;
    }

    // WebP: 'R''I''F''F' [4바이트 크기] 'W''E''B''P'
    private static boolean isWebp(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
