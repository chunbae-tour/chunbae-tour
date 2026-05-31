package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.response.ShopImageResponse;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.Set;

/**
 * 가게 사진 업로드 서비스 (KAN-188).
 * 파일 유효성 검사 + ShopImageStorage 위임 구조 — S3 통합 시 NoOpShopImageStorage를 S3ShopImageStorage로 교체.
 *
 * [트랜잭션 경계]
 * uploadImage()는 DB 변경 없이 조회(소유권 확인) + 외부 저장소 업로드만 수행하므로 @Transactional 불필요.
 * S3 통합 후 imageUrl을 Shop 또는 별도 엔티티에 저장한다면 그 시점에 @Transactional 추가 검토.
 * 단, S3 업로드는 트랜잭션 밖에서 처리해 "업로드 성공 + DB 저장 실패" 시 S3 고아 파일 문제를 명시적으로 설계해야 함.
 *
 * [보안 경계 — TODO]
 * SECURITY(KAN-188): MultipartFile.getContentType()은 브라우저 선언값(user-supplied)으로 .exe를
 * image/jpeg로 위장 가능. S3 통합 전 Apache Tika 또는 URLConnection.guessContentTypeFromStream()으로
 * magic-byte sniffing 추가 필수. 현재는 stub이므로 S3에 저장되지 않아 위협 없음.
 */
@Service
@RequiredArgsConstructor
public class ShopImageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5MB
    /** "image/jpg"는 비표준 MIME — 브라우저는 .jpg/.jpeg 모두 image/jpeg로 전송하므로 포함 불필요 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private final ShopRepository shopRepository;
    private final ShopImageStorage imageStorage;

    /**
     * 가게 사진 업로드.
     * 소유권 확인 + 파일 유효성 검사 후 imageStorage에 위임.
     * 현재 stub(NoOpShopImageStorage) — S3 통합 전까지 EXTERNAL_SERVICE_ERROR(503) 반환.
     *
     * [파일명 정책 — TODO]
     * PATH TRAVERSAL: 후속 S3 통합 시 originalFilename은 절대 key 경로에 사용 금지.
     * 반드시 UUID.randomUUID().toString() + 확장자만 사용할 것:
     *   String key = "shops/" + shopId + "/" + UUID.randomUUID() + extractExtension(file);
     */
    public ShopImageResponse uploadImage(Long userId, Long shopId, MultipartFile file) {
        // 소유권 확인 — 타인 가게 이미지 업로드 차단
        shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // 파일 유효성 검사
        validateFile(file);

        String url = imageStorage.upload(shopId, file);
        return new ShopImageResponse(url);
    }

    private void validateFile(MultipartFile file) {
        // Spring @RequestParam 누락 시 서비스 도달 전 400 반환 — null은 실제 발생하지 않음
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.SHOP_IMAGE_FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.SHOP_IMAGE_FILE_TOO_LARGE);
        }
        // Content-Type은 user-supplied — magic-byte sniffing으로 보강 필요 (상단 TODO 참고)
        // null 방어: per-part Content-Type 헤더 없으면 getContentType()이 null 반환 → Set.contains(null) NPE 차단
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.SHOP_IMAGE_TYPE_UNSUPPORTED);
        }
    }
}
