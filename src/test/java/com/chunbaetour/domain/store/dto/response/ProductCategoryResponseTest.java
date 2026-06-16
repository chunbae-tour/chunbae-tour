package com.chunbaetour.domain.store.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.chunbaetour.domain.store.type.ProductCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductCategoryResponseTest {

    @Test
    @DisplayName("상품 카테고리를 code와 label 응답으로 변환한다")
    void from_convertsCategory() {
        ProductCategoryResponse response = ProductCategoryResponse.from(ProductCategory.EXPERIENCE);

        assertThat(response.code()).isEqualTo("EXPERIENCE");
        assertThat(response.label()).isEqualTo("체험");
    }

    @Test
    @DisplayName("상품 카테고리가 null이면 불변식 위반 예외가 발생한다")
    void from_nullCategory_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> ProductCategoryResponse.from(null))
                .withMessage("category must not be null");
    }
}
