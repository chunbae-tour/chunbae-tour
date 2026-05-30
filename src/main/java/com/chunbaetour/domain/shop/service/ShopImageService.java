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
 * S3 업로드 구현 예정 — 현재 stub 상태.
 *
 * TODO(KAN-188): S3 의존성(spring-cloud-aws 또는 aws-java-sdk-s3) 추가 후 아래 upload() 구현 완성.
 *   - build.gradle: implementation 'io.awspring.cloud:spring-cloud-aws-s3'
 *   - application.yml: spring.cloud.aws.s3.bucket, credentials 설정 필요
 *   - UUID 기반 파일명, ContentType 설정, public-read ACL or presigned URL 정책 결정 필요
 */
@Service
@RequiredArgsConstructor
public class ShopImageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp");

    private final ShopRepository shopRepository;

    /**
     * 가게 사진 업로드.
     * 소유권 확인 + 파일 유효성 검사 후 S3 업로드 → URL 반환.
     *
     * TODO(KAN-188): S3 설정 완료 후 주석 제거 및 실제 업로드 로직 구현.
     */
    public ShopImageResponse uploadImage(Long userId, Long shopId, MultipartFile file) {
        // 소유권 확인 — 타인 가게 이미지 업로드 차단
        shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // 파일 유효성 검사
        validateFile(file);

        // TODO(KAN-188): S3 업로드 구현 후 아래 예외 제거
        // String fileName = UUID.randomUUID() + getExtension(file.getOriginalFilename());
        // String url = s3Client.upload(bucket, "shops/" + shopId + "/" + fileName, file);
        // return new ShopImageResponse(url);
        throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
