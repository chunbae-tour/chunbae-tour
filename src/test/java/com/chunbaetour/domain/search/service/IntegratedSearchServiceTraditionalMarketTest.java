package com.chunbaetour.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import com.chunbaetour.domain.search.dto.response.integrated.IntegratedSearchItem;
import com.chunbaetour.domain.search.dto.response.integrated.IntegratedTraditionalMarketItem;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class IntegratedSearchServiceTraditionalMarketTest extends AbstractIntegrationTest {

    @Autowired
    private IntegratedSearchService integratedSearchService;

    @Autowired
    private TraditionalMarketRepository marketRepository;

    @AfterEach
    void cleanup() {
        marketRepository.deleteAll();
    }

    @DisplayName("통합 검색: TRADITIONAL_MARKET 타입으로 시장 조회")
    @Test
    void searchIntegrated_traditionalMarketType() {
        // given
        TraditionalMarket market = TraditionalMarket.builder()
                .name("동대문시장")
                .address("서울 중구 을지로 203")
                .lat(BigDecimal.valueOf(37.5700))
                .lng(BigDecimal.valueOf(127.0099))
                .marketType("상설장")
                .phoneNumber("02-1234-5678")
                .establishYear(1960)
                .build();

        marketRepository.save(market);

        // when
        CursorPageResponse<IntegratedSearchItem> response = integratedSearchService.searchIntegrated(
                "동대문시장",
                "TRADITIONAL_MARKET",
                null,
                10
        );

        // then
        assertThat(response.content())
                .hasSize(1)
                .allMatch(item -> item.getTargetType().equals("TRADITIONAL_MARKET"));

        IntegratedTraditionalMarketItem item = (IntegratedTraditionalMarketItem) response.content().get(0);
        assertThat(item.name()).isEqualTo("동대문시장");
        assertThat(item.address()).isEqualTo("서울 중구 을지로 203");
    }

    @DisplayName("통합 검색: 빈 키워드 → 예외")
    @Test
    void searchIntegrated_emptyKeyword_throwException() {
        // when & then
        assertThatThrownBy(() ->
                integratedSearchService.searchIntegrated("", "TRADITIONAL_MARKET", null, 10)
        ).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @DisplayName("통합 검색: 무효한 타입 → 예외")
    @Test
    void searchIntegrated_invalidType_throwException() {
        // when & then
        assertThatThrownBy(() ->
                integratedSearchService.searchIntegrated("시장", "INVALID_TYPE", null, 10)
        ).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
