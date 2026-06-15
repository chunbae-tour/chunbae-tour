package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.response.ShopImageResponse;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.storage.ShopImageStorage;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.Set;

/**
 * 가게 사진 업로드 서비스 (KAN-188, E10).
 * 파일 유효성 검사 + ShopImageStorage 위임 구조 — 운영=S3ShopImageStorage(@Profile prod), 그 외=LocalDiskShopImageStorage.
 * 반환값은 접근 URL이 아니라 <b>객체 키</b>다(조회 시 presigned GET으로 변환 — PR3).
 *
 * [트랜잭션 경계]
 * uploadImage()는 DB 변경 없이 조회(소유권 확인) + 외부 저장소 업로드만 수행하므로 @Transactional 불필요.
 * 키 영속화는 별도 가게 수정 API(imageUrls)가 담당 — 업로드는 키만 반환한다(고아 객체 문제는 키 미저장으로 회피).
 *
 * [보안 경계]
 * SECURITY(KAN-188): MultipartFile.getContentType()은 브라우저 선언값(user-supplied)이라 .exe를 image/jpeg로 위장 가능.
 * declared content-type 화이트리스트 + magic-byte 시그니처 검사(JPEG/PNG/WebP)를 함께 적용해 위장 업로드를 차단한다
 * (경량 시그니처 검사 — Apache Tika 미사용).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopImageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5MB
    /** "image/jpg"는 비표준 MIME — 브라우저는 .jpg/.jpeg 모두 image/jpeg로 전송하므로 포함 불필요 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private final ShopRepository shopRepository;
    private final ShopImageStorage imageStorage;

    /**
     * 가게 사진 업로드.
     * 소유권 확인 + 파일 유효성 검사(크기·content-type·magic-byte) 후 imageStorage에 위임하고 객체 키를 반환한다.
     * 파일명은 originalFilename을 쓰지 않고 ShopImageKeys가 UUID로 재생성한다(path traversal 차단).
     */
    public ShopImageResponse uploadImage(Long userId, Long shopId, MultipartFile file) {
        // 소유권 확인 — 타인 가게 이미지 업로드 차단
        shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // 파일 유효성 검사
        validateFile(file);

        String objectKey = imageStorage.upload(shopId, file);
        return new ShopImageResponse(objectKey);
    }

    private void validateFile(MultipartFile file) {
        // Spring @RequestParam 누락 시 서비스 도달 전 400 반환 — null은 실제 발생하지 않음
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.SHOP_IMAGE_FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.SHOP_IMAGE_FILE_TOO_LARGE);
        }
        // 1차: declared Content-Type 화이트리스트. null 방어: per-part 헤더 없으면 getContentType()=null.
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.SHOP_IMAGE_TYPE_UNSUPPORTED);
        }
        // 2차: magic-byte 시그니처 — declared가 image/jpeg여도 실제 바이트가 이미지가 아니면 거부(.exe 위장 차단, KAN-188).
        if (!isAllowedImageSignature(file)) {
            throw new BusinessException(ErrorCode.SHOP_IMAGE_TYPE_UNSUPPORTED);
        }
    }

    /** 파일 앞부분 매직바이트가 허용 이미지(JPEG/PNG/WebP) 시그니처인지 검사. */
    private static boolean isAllowedImageSignature(MultipartFile file) {
        byte[] h;
        try {
            h = file.getInputStream().readNBytes(12);
        } catch (IOException e) {
            return false;
        }
        return isJpeg(h) || isPng(h) || isWebp(h);
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
