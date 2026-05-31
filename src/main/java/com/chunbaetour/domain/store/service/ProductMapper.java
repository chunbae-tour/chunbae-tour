package com.chunbaetour.domain.store.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.store.dto.response.ProductDetailResponse;
import com.chunbaetour.domain.store.entity.Product;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 상품 DTO 변환 공유 컴포넌트 — ProductService / AdminProductService 중복 제거 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final ObjectMapper objectMapper;

    public ProductDetailResponse toDetail(Product p) {
        return toDetail(p, parseImageUrls(p.getImageUrls()));
    }

    /** 쓰기(create/update) 응답용 — imageUrls 재파싱 없이 원본 리스트 직접 사용해 DB↔응답 불일치 방지 */
    public ProductDetailResponse toDetail(Product p, List<String> imageUrls) {
        return new ProductDetailResponse(
                p.getId(), p.getName(), p.getDescription(), p.getCategory(),
                p.getPrice(), p.getOriginalPrice(), imageUrls,
                p.getMerchantName(), p.getStock(),
                Math.max(0, p.getOriginalStock() - p.getStock()),
                p.getValidityDays(), p.getStatus());
    }

    public List<String> parseImageUrls(String imageUrlsJson) {
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

    public String serializeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(imageUrls);
        } catch (JacksonException e) {
            log.error("[상품] imageUrls 직렬화 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
