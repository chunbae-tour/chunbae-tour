package com.chunbaetour.domain.store.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.store.dto.request.AdminProductCreateRequest;
import com.chunbaetour.domain.store.dto.request.AdminProductUpdateRequest;
import com.chunbaetour.domain.store.dto.response.ProductDetailResponse;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * 관리자 상품 CRUD 서비스.
 * 등록/수정/삭제 후 Redis 상품 상세 캐시 무효화.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private static final String CACHE_KEY_PREFIX = "product:";

    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 상품 등록 — status = ON_SALE, originalStock = stock */
    @Transactional
    public ProductDetailResponse createProduct(AdminProductCreateRequest request) {
        Product product = productRepository.save(Product.create(request));
        return toDetail(product);
    }

    /**
     * 상품 수정 — null 필드 무시, 수정 후 캐시 무효화.
     * HIDDEN 상품도 수정 가능 — 관리자가 복구 시 status = ON_SALE 명시.
     */
    @Transactional
    public ProductDetailResponse updateProduct(Long productId, AdminProductUpdateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.adminUpdate(request);
        evictCache(productId);
        return toDetail(product);
    }

    /**
     * 상품 삭제 — soft delete (status = HIDDEN).
     * 캐시에 HIDDEN 상태로 저장되어 이후 조회 시 PRODUCT_NOT_FOUND 반환.
     */
    @Transactional
    public void deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.softDelete();
        evictCache(productId);
    }

    private void evictCache(Long productId) {
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + productId);
        } catch (Exception e) {
            log.warn("[관리자 상품] 캐시 무효화 실패 (productId: {})", productId, e);
        }
    }

    private ProductDetailResponse toDetail(Product p) {
        return new ProductDetailResponse(
                p.getId(), p.getName(), p.getDescription(), p.getCategory(),
                p.getPrice(), p.getOriginalPrice(), parseImageUrls(p.getImageUrls()),
                p.getMerchantName(), p.getStock(),
                Math.max(0, p.getOriginalStock() - p.getStock()),
                p.getValidityDays(), p.getStatus());
    }

    private List<String> parseImageUrls(String imageUrlsJson) {
        if (imageUrlsJson == null || imageUrlsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(imageUrlsJson, new TypeReference<List<String>>() {})
                    .stream()
                    .filter(url -> url != null && !url.isBlank())
                    .toList();
        } catch (JacksonException e) {
            log.warn("[관리자 상품] imageUrls 파싱 실패: {}", imageUrlsJson);
            return List.of();
        }
    }
}
