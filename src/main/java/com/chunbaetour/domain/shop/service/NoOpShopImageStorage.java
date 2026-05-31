package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * ShopImageStorage stub 구현체 — S3 설정 완료 전까지 사용.
 * S3ShopImageStorage로 교체 시 이 bean을 제거하거나 @ConditionalOnProperty로 비활성화.
 *
 * TODO(KAN-188): S3 의존성 추가 후 S3ShopImageStorage 구현 + 이 클래스 제거.
 *   - build.gradle: implementation 'io.awspring.cloud:spring-cloud-aws-s3'
 *   - application.yml: spring.cloud.aws.s3.bucket, credentials 설정
 */
@Component
public class NoOpShopImageStorage implements ShopImageStorage {

    @Override
    public String upload(Long shopId, MultipartFile file) {
        throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
    }
}
