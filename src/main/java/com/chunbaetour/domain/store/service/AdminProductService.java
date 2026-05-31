package com.chunbaetour.domain.store.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.store.dto.request.AdminProductCreateRequest;
import com.chunbaetour.domain.store.dto.request.AdminProductUpdateRequest;
import com.chunbaetour.domain.store.dto.response.ProductDetailResponse;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 관리자 상품 CRUD 서비스.
 * 등록/수정/삭제 후 Redis 상품 상세 캐시 무효화.
 * 수정/삭제는 PESSIMISTIC_WRITE 락 사용 — 동시 구매 decrement와의 lost-update 방지.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private static final String CACHE_KEY_PREFIX = "product:";

    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;
    private final ProductMapper productMapper;

    /**
     * 상품 등록 — status = ON_SALE, originalStock = stock.
     * 응답 imageUrls는 request 원본 리스트를 직접 사용 — DB 재파싱으로 인한 응답 불일치 방지.
     */
    @Transactional
    public ProductDetailResponse createProduct(AdminProductCreateRequest request) {
        String imageUrlsJson = productMapper.serializeImageUrls(request.imageUrls());
        Product product = productRepository.save(Product.create(
                request.name(), request.description(), request.category(),
                request.price(), request.originalPrice(), request.stock(),
                imageUrlsJson, request.merchantName(), request.validityDays(), request.maxPerPerson()
        ));
        List<String> imageUrls = request.imageUrls() != null ? request.imageUrls() : List.of();
        return productMapper.toDetail(product, imageUrls);
    }

    /**
     * 상품 수정 — null 필드 무시, 수정 후 캐시 무효화.
     * HIDDEN 상품도 수정 가능 — 관리자가 복구 시 status = ON_SALE 명시.
     * 응답 imageUrls: 수정 요청에 포함된 경우 원본 리스트, 미포함 시 기존 DB 값 파싱.
     */
    @Transactional
    public ProductDetailResponse updateProduct(Long productId, AdminProductUpdateRequest request) {
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        String imageUrlsJson = request.imageUrls() != null
                ? productMapper.serializeImageUrls(request.imageUrls()) : null;
        product.adminUpdate(
                request.name(), request.description(), request.category(),
                request.price(), request.originalPrice(), request.stock(),
                imageUrlsJson, request.merchantName(), request.validityDays(),
                request.maxPerPerson(), request.status()
        );
        evictCacheAfterCommit(productId);
        List<String> imageUrls = request.imageUrls() != null
                ? request.imageUrls()
                : productMapper.parseImageUrls(product.getImageUrls());
        return productMapper.toDetail(product, imageUrls);
    }

    /**
     * 상품 삭제 — soft delete (status = HIDDEN).
     * 204 대신 200 + ProductDetailResponse(status=HIDDEN) 반환 — 실제 삭제가 아닌 숨김 처리임을 명시.
     */
    @Transactional
    public ProductDetailResponse deleteProduct(Long productId) {
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.softDelete();
        evictCacheAfterCommit(productId);
        return productMapper.toDetail(product);
    }

    /**
     * 트랜잭션 커밋 후 캐시 무효화 등록.
     * 커밋 전 삭제 시 커밋~삭제 사이 조회가 stale 값을 재적재하는 문제 방지.
     * 트랜잭션 컨텍스트 없으면 즉시 무효화(테스트 등 비트랜잭션 호출 대응).
     */
    private void evictCacheAfterCommit(Long productId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictCache(productId);
                }
            });
        } else {
            evictCache(productId);
        }
    }

    private void evictCache(Long productId) {
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + productId);
        } catch (Exception e) {
            log.warn("[관리자 상품] 캐시 무효화 실패 (productId: {})", productId, e);
        }
    }
}
