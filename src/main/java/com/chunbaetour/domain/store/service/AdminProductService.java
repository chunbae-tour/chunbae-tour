package com.chunbaetour.domain.store.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.store.dto.request.AdminProductCreateRequest;
import com.chunbaetour.domain.store.dto.request.AdminProductUpdateRequest;
import com.chunbaetour.domain.store.dto.response.ProductDetailResponse;
import com.chunbaetour.domain.store.dto.response.ProductSummaryResponse;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.type.ProductStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
    private static final String STOCK_KEY_PREFIX = "stock:";

    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;
    private final ProductMapper productMapper;

    /**
     * 관리자 상품 목록 조회 (KAN-300).
     * 공개 목록과 달리 전체 status 노출 — HIDDEN(숨김 처리) 상품 포함해 관리/복구 가능.
     * status 미지정 시 전체 상태, category 미지정 시 전체 카테고리. cursor 기반 keyset 페이징.
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<ProductSummaryResponse> getProducts(
            ProductStatus status, String category, String cursor, int size) {
        String normalizedCategory = (category == null || category.isBlank()) ? null : category.trim();
        Long cursorId = CursorUtils.decodeSafe(cursor);

        // size+1 조회 → 다음 페이지 존재 여부 판별
        List<Product> products = productRepository.findForAdmin(
                status, normalizedCategory, cursorId, PageRequest.of(0, size + 1));

        // 다음 페이지 판별·매핑·커서 인코딩을 공통 팩토리로 위임 (KAN-295)
        return CursorPageResponse.of(products, size, productMapper::toSummary, Product::getId);
    }

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
        // 상품 등록 시 Redis 재고 키 초기화 — 구매 1단계(Redis DECR) 선점 활성화
        setStockKeyAfterCommit(product.getId(), product.getStock());
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

        // null 필드는 기존 엔티티 값으로 병합 후 price/originalPrice 교차 검증.
        // DTO isOriginalPriceValid()는 요청 내 필드만 비교하므로, price=null 요청 시 기존 price와의 비교가 불가.
        // 서비스에서 병합 값으로 사전 검증해 ConstraintViolation과 동일한 400 응답 보장.
        long effectivePrice = request.price() != null ? request.price() : product.getPrice();
        Long effectiveOriginalPrice = request.originalPrice() != null ? request.originalPrice() : product.getOriginalPrice();
        if (effectiveOriginalPrice != null && effectiveOriginalPrice < effectivePrice) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        String imageUrlsJson = request.imageUrls() != null
                ? productMapper.serializeImageUrls(request.imageUrls()) : null;
        product.adminUpdate(
                request.name(), request.description(), request.category(),
                request.price(), request.originalPrice(), request.stock(),
                imageUrlsJson, request.merchantName(), request.validityDays(),
                request.maxPerPerson(), request.status()
        );
        evictCacheAfterCommit(productId);

        // HIDDEN 전환과 재고 갱신은 배타적 — HIDDEN+stock 동시 요청 시 삭제만 실행해 숨김 상품에 키 잔존 방지.
        // Note: afterCommit SET이 동시 구매 DECR보다 늦게 실행될 경우 Redis 재고가 실제보다 많아질 수 있음.
        // 구매 2단계에서 DB 비관적 락으로 최종 검증하므로 오버셀링 위험 없음(Best-Effort 정책).
        if (request.status() == ProductStatus.HIDDEN) {
            // HIDDEN 전환 시 stock 키 삭제 — 이후 구매 경로 진입 차단
            deleteStockKeyAfterCommit(productId);
        } else if (request.stock() != null || request.status() == ProductStatus.ON_SALE) {
            // 재고 변경 또는 ON_SALE 재활성화 시 stock 키 갱신
            // HIDDEN/SOLD_OUT → ON_SALE(stock=null) 재활성화 경로에서 키 없으면 구매 1단계(Redis DECR) 차단 방지
            setStockKeyAfterCommit(productId, product.getStock());
        }
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
        // HIDDEN 처리 시 Redis 재고 키 삭제 — 이후 구매 경로 진입 차단
        deleteStockKeyAfterCommit(productId);
        return productMapper.toDetail(product);
    }

    /**
     * 트랜잭션 커밋 후 action 실행 등록.
     * 트랜잭션 컨텍스트 없으면 즉시 실행(테스트 등 비트랜잭션 호출 대응).
     */
    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void evictCacheAfterCommit(Long productId) {
        registerAfterCommit(() -> evictCache(productId));
    }

    private void evictCache(Long productId) {
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + productId);
        } catch (Exception e) {
            log.warn("[관리자 상품] 캐시 무효화 실패 (productId: {})", productId, e);
        }
    }

    private void setStockKeyAfterCommit(Long productId, int stock) {
        registerAfterCommit(() -> setStockKey(productId, stock));
    }

    private void setStockKey(Long productId, int stock) {
        try {
            redisTemplate.opsForValue().set(STOCK_KEY_PREFIX + productId, String.valueOf(stock));
        } catch (Exception e) {
            log.warn("[관리자 상품] Redis 재고 키 세팅 실패 (productId: {})", productId, e);
        }
    }

    private void deleteStockKeyAfterCommit(Long productId) {
        registerAfterCommit(() -> deleteStockKey(productId));
    }

    private void deleteStockKey(Long productId) {
        try {
            redisTemplate.delete(STOCK_KEY_PREFIX + productId);
        } catch (Exception e) {
            log.warn("[관리자 상품] Redis 재고 키 삭제 실패 (productId: {})", productId, e);
        }
    }
}
