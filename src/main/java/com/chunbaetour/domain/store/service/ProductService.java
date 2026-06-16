package com.chunbaetour.domain.store.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.store.dto.response.ProductCategoryResponse;
import com.chunbaetour.domain.store.dto.response.ProductDetailResponse;
import com.chunbaetour.domain.store.dto.response.ProductSummaryResponse;
import com.chunbaetour.domain.store.entity.Product;
import com.chunbaetour.domain.store.repository.ProductRepository;
import com.chunbaetour.domain.store.type.ProductCategory;
import com.chunbaetour.domain.store.type.ProductStatus;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
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
    private final ProductMapper productMapper;

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String CACHE_KEY_PREFIX = "product:";

    // TODO(cache): 상품 목록도 TTL 5분 캐싱 필요 — category+cursorId+size 복합 키로 구현
    //   캐시 무효화 시점: 상품 상태 변경(STORY-17 구매 후 SOLD_OUT 전환) 시 관련 키 전체 삭제 고려
    // ON_SALE·SOLD_OUT만 공개 — 신규 status 추가 시 여기서 의도적으로 추가해야 노출됨
    private static final Set<ProductStatus> VISIBLE_STATUSES =
            Set.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT);

    /**
     * 상품 목록 조회.
     * VISIBLE_STATUSES(ON_SALE·SOLD_OUT) 화이트리스트. category 없으면 전체. cursor 없으면 첫 페이지.
     * stock=0이어도 status=ON_SALE 이면 목록에 노출 — 품절 상태 전이는 STORY-17(구매 흐름)에서 처리.
     */
    public CursorPageResponse<ProductSummaryResponse> getProducts(ProductCategory category, String cursor, int size) {
        Long cursorId = CursorUtils.decodeSafe(cursor);

        // size+1 조회 → 다음 페이지 존재 여부 판별. category null이면 전체.
        List<Product> products = productRepository.findVisibleProducts(
                VISIBLE_STATUSES, category, cursorId, PageRequest.of(0, size + 1));

        // 다음 페이지 판별·매핑·커서 인코딩을 공통 팩토리로 위임 (KAN-295)
        return CursorPageResponse.of(products, size, this::toSummary, Product::getId);
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
                    if (cachedResponse.status() == ProductStatus.HIDDEN) {
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

        // 캐시 저장 (TTL 5분) — best-effort, 저장 실패가 DB 응답에 영향 주지 않음
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (Exception e) {
            log.warn("[상품 상세] 캐시 저장 실패 — DB 응답은 정상 (productId: {})", productId, e);
        }

        return response;
    }

    /** 목록 조회용 경량 DTO 변환 — 이미지는 첫 번째 URL만, soldCount = originalStock - stock */
    private ProductSummaryResponse toSummary(Product p) {
        List<String> urls = productMapper.parseImageUrls(p.getImageUrls());
        return new ProductSummaryResponse(
                p.getId(), p.getName(), ProductCategoryResponse.from(p.getCategory()), p.getPrice(), p.getOriginalPrice(),
                urls.isEmpty() ? null : urls.get(0), p.getMerchantName(),
                p.getStock(), Math.max(0, p.getOriginalStock() - p.getStock()),
                p.getStatus());
    }

    /** 상세 조회용 풀 DTO 변환 — 이미지 전체 목록, description/validityDays/status 포함 */
    private ProductDetailResponse toDetail(Product p) {
        return productMapper.toDetail(p);
    }
}
