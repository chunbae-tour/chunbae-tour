package com.chunbaetour.domain.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SearchQueryRepositoryTraditionalMarketTest extends AbstractIntegrationTest {

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private TraditionalMarketRepository marketRepository;

    @AfterEach
    void cleanup() {
        marketRepository.deleteAll();
    }

    @DisplayName("시장명 검색: 키워드 포함하는 시장 조회")
    @Test
    void searchTraditionalMarkets_byKeyword() {
        // given
        TraditionalMarket market1 = TraditionalMarket.builder()
                .name("동대문시장")
                .address("서울 중구 을지로 203")
                .lat(BigDecimal.valueOf(37.5700))
                .lng(BigDecimal.valueOf(127.0099))
                .marketType("상설장")
                .build();

        TraditionalMarket market2 = TraditionalMarket.builder()
                .name("남대문시장")
                .address("서울 중구 덕수궁길 37")
                .lat(BigDecimal.valueOf(37.5650))
                .lng(BigDecimal.valueOf(127.0100))
                .marketType("상설장")
                .build();

        marketRepository.saveAll(List.of(market1, market2));

        // when
        List<TraditionalMarket> results = searchQueryRepository.searchTraditionalMarkets("대문");

        // then
        assertThat(results).hasSize(2);
    }

    @DisplayName("시장명 검색: 빈 키워드 → 결과 없음")
    @Test
    void searchTraditionalMarkets_emptyKeyword_returnEmpty() {
        // given
        TraditionalMarket market = TraditionalMarket.builder()
                .name("동대문시장")
                .address("서울")
                .lat(BigDecimal.valueOf(37.5700))
                .lng(BigDecimal.valueOf(127.0099))
                .build();

        marketRepository.save(market);

        // when
        List<TraditionalMarket> results = searchQueryRepository.searchTraditionalMarkets("");

        // then
        assertThat(results).isEmpty();
    }
}
