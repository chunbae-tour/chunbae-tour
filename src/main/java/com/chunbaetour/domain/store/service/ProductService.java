package com.chunbaetour.domain.store.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.store.dto.response.ProductDetailResponse;
import com.chunbaetour.domain.store.dto.response.ProductSummaryResponse;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.type.ProductStatus;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 스토어 상품 조회 서비스 (STORY-16).
 * 상품 목록(cursor 페이징) 및 상세 조회 제공.
 * 상품 상세는 Redis TTL 5분 캐싱 적용 — 재고 변경 시 STORY-17에서 캐시 무효화.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String CACHE_KEY_PREFIX = "product:";

    // TODO(cache): 상품 목록도 TTL 5분 캐싱 필요 — category+cursorId+size 복합 키로 구현
    //   캐시 무효화 시점: 상품 상태 변경(STORY-17 구매 후 SOLD_OUT 전환) 시 관련 키 전체 삭제 고려
    /**
     * 상품 목록 조회.
     * HIDDEN 상품 제외. category 없으면 전체. cursor 없으면 첫 페이지.
     */
    public CursorPageResponse<ProductSummaryResponse> getProducts(String category, String cursor, int size) {
        String normalizedCategory = (category == null || category.isBlank()) ? null : category.trim();
        Long cursorId = CursorUtils.decodeSafe(cursor);

        // size+1 조회 → 다음 페이지 존재 여부 판별
        List<Product> products = productRepository.findVisibleProducts(
                ProductStatus.HIDDEN, normalizedCategory, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = products.size() > size;
        List<Product> page = hasNext ? products.subList(0, size) : products;

        List<ProductSummaryResponse> content = page.stream()
                .map(this::toSummary)
                .toList();

        String nextCursor = hasNext ? CursorUtils.encode(page.get(page.size() - 1).getId()) : null;
        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }

    /**
     * 상품 상세 조회.
     * Redis 캐시 우선 확인 → 미스 시 DB 조회 후 캐시 저장.
     * HIDDEN 상품은 존재하지 않는 것으로 처리(PRODUCT_NOT_FOUND).
     */
    public ProductDetailResponse getProduct(Long productId) {
        // Redis 캐시 확인 — 연결 실패/타임아웃 시 Exception catch 후 DB 폴백으로 서비스 지속
        String cacheKey = CACHE_KEY_PREFIX + productId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    ProductDetailResponse cachedResponse = objectMapper.readValue(cached, ProductDetailResponse.class);
                    // 캐시된 status 확인 — HIDDEN이면 노출 차단 (DB 재조회 없이 캐시 내 status 기준)
                    if ("HIDDEN".equals(cachedResponse.status())) {
                        throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
                    }
                    return cachedResponse;
                } catch (JacksonException e) {
                    log.warn("[상품 상세] 캐시 역직렬화 실패, DB 폴백 (productId: {})", productId);
                }
            }
        } catch (BusinessException e) {
            // HIDDEN 상태 차단 예외는 그대로 전파 — DB 폴백 대상 아님
            throw e;
        } catch (Exception e) {
            log.warn("[상품 상세] Redis 연결 실패, DB 폴백 (productId: {})", productId, e);
        }

        // DB 조회
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // HIDDEN 상품 — 클라이언트에 노출 금지
        if (product.getStatus() == ProductStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        ProductDetailResponse response = toDetail(product);

        // 캐시 저장 (TTL 5분)
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (JacksonException e) {
            log.warn("[상품 상세] 캐시 직렬화 실패 (productId: {})", productId);
        }

        return response;
    }

    /** 목록 조회용 경량 DTO 변환 — 이미지는 첫 번째 URL만, soldCount = originalStock - stock */
    private ProductSummaryResponse toSummary(Product p) {
        List<String> urls = parseImageUrls(p.getImageUrls());
        return new ProductSummaryResponse(
                p.getId(), p.getName(), p.getCategory(), p.getPrice(), p.getOriginalPrice(),
                urls.isEmpty() ? null : urls.get(0), p.getMerchantName(),
                p.getStock(), Math.max(0, p.getOriginalStock() - p.getStock()),
                p.getStatus().name());
    }

    /** 상세 조회용 풀 DTO 변환 — 이미지 전체 목록, description/validityDays/status 포함 */
    private ProductDetailResponse toDetail(Product p) {
        return new ProductDetailResponse(
                p.getId(), p.getName(), p.getDescription(), p.getCategory(),
                p.getPrice(), p.getOriginalPrice(), parseImageUrls(p.getImageUrls()),
                p.getMerchantName(), p.getStock(),
                Math.max(0, p.getOriginalStock() - p.getStock()),
                p.getValidityDays(), p.getStatus().name());
    }

    /** imageUrls JSON 배열 문자열 → List<String> 파싱 (실패 시 빈 리스트 반환) */
    private List<String> parseImageUrls(String imageUrlsJson) {
        if (imageUrlsJson == null || imageUrlsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(imageUrlsJson, new TypeReference<List<String>>() {})
                    .stream()
                    .filter(url -> url != null && !url.isBlank())
                    .toList();
        } catch (JacksonException e) {
            log.warn("[상품] imageUrls 파싱 실패: {}", imageUrlsJson);
            return List.of();
        }
    }
}
