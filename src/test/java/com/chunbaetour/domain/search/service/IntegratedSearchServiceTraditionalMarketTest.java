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
import com.chunbaetour.domain.search.repository.SearchQueryRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class IntegratedSearchServiceTraditionalMarketTest {

    @Autowired
    private IntegratedSearchService integratedSearchService;

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private TraditionalMarketRepository marketRepository;

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
        assertThat(item.marketType()).isEqualTo("상설장");
    }

    @DisplayName("통합 검색: ALL 타입에 TRADITIONAL_MARKET 포함")
    @Test
    void searchIntegrated_allType_includesTraditionalMarket() {
        // given
        TraditionalMarket market = TraditionalMarket.builder()
                .name("동대문시장")
                .address("서울")
                .lat(BigDecimal.valueOf(37.5700))
                .lng(BigDecimal.valueOf(127.0099))
                .build();

        marketRepository.save(market);

        // when
        CursorPageResponse<IntegratedSearchItem> response = integratedSearchService.searchIntegrated(
                "동대문시장",
                "ALL",
                null,
                10
        );

        // then
        assertThat(response.content())
                .anyMatch(item -> item.getTargetType().equals("TRADITIONAL_MARKET"));
    }

    @DisplayName("통합 검색: 시장 정확한 일치 높은 점수")
    @Test
    void searchIntegrated_exactMatch_prioritized() {
        // given: 정확히 일치 + 부분 일치
        TraditionalMarket exact = TraditionalMarket.builder()
                .name("동대문시장")
                .address("서울1")
                .lat(BigDecimal.valueOf(37.5700))
                .lng(BigDecimal.valueOf(127.0099))
                .build();

        TraditionalMarket partial = TraditionalMarket.builder()
                .name("동대문 안경시장")
                .address("서울2")
                .lat(BigDecimal.valueOf(37.5710))
                .lng(BigDecimal.valueOf(127.0100))
                .build();

        marketRepository.saveAll(List.of(exact, partial));

        // when
        CursorPageResponse<IntegratedSearchItem> response = integratedSearchService.searchIntegrated(
                "동대문시장",
                "TRADITIONAL_MARKET",
                null,
                10
        );

        // then: 정확히 일치하는 것이 먼저
        assertThat(response.content())
                .isNotEmpty()
                .first()
                .extracting(item -> ((IntegratedTraditionalMarketItem) item).name())
                .isEqualTo("동대문시장");
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

    @DisplayName("통합 검색: 50자 초과 키워드 → 예외")
    @Test
    void searchIntegrated_keywordExceed50Chars_throwException() {
        // given
        String longKeyword = "a".repeat(51);

        // when & then
        assertThatThrownBy(() ->
                integratedSearchService.searchIntegrated(longKeyword, "TRADITIONAL_MARKET", null, 10)
        ).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @DisplayName("통합 검색: 커서 기반 페이징")
    @Test
    void searchIntegrated_cursorPaging() {
        // given: 3개 시장 저장
        List<TraditionalMarket> markets = List.of(
                TraditionalMarket.builder()
                        .name("시장1")
                        .address("주소1")
                        .lat(BigDecimal.valueOf(37.5700))
                        .lng(BigDecimal.valueOf(127.0099))
                        .build(),
                TraditionalMarket.builder()
                        .name("시장2")
                        .address("주소2")
                        .lat(BigDecimal.valueOf(37.5710))
                        .lng(BigDecimal.valueOf(127.0100))
                        .build(),
                TraditionalMarket.builder()
                        .name("시장3")
                        .address("주소3")
                        .lat(BigDecimal.valueOf(37.5720))
                        .lng(BigDecimal.valueOf(127.0101))
                        .build()
        );
        marketRepository.saveAll(markets);

        // when: 첫 페이지 (size=2)
        CursorPageResponse<IntegratedSearchItem> page1 = integratedSearchService.searchIntegrated(
                "시장",
                "TRADITIONAL_MARKET",
                null,
                2
        );

        // then: 첫 2개 + nextCursor 있음
        assertThat(page1.content()).hasSize(2);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.nextCursor()).isNotNull();

        // when: 두 번째 페이지
        CursorPageResponse<IntegratedSearchItem> page2 = integratedSearchService.searchIntegrated(
                "시장",
                "TRADITIONAL_MARKET",
                page1.nextCursor(),
                2
        );

        // then: 나머지 1개
        assertThat(page2.content()).hasSize(1);
        assertThat(page2.hasNext()).isFalse();
    }
}
