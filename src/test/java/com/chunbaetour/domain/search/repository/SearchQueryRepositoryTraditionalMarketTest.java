package com.chunbaetour.domain.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SearchQueryRepositoryTraditionalMarketTest extends AbstractIntegrationTest {

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private TraditionalMarketRepository marketRepository;

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

        TraditionalMarket market3 = TraditionalMarket.builder()
                .name("경동시장")
                .address("서울 중구 봉래길 56")
                .lat(BigDecimal.valueOf(37.5640))
                .lng(BigDecimal.valueOf(127.0110))
                .marketType("4일장")
                .build();

        marketRepository.saveAll(List.of(market1, market2, market3));

        // when: "대문" 검색
        List<TraditionalMarket> results = searchQueryRepository.searchTraditionalMarkets("대문");

        // then: 동대문, 남대문 포함 (2개)
        assertThat(results)
                .hasSize(2)
                .extracting(TraditionalMarket::getName)
                .containsExactlyInAnyOrder("동대문시장", "남대문시장");
    }

    @DisplayName("시장명 검색: 대소문자 무시")
    @Test
    void searchTraditionalMarkets_caseInsensitive() {
        // given
        TraditionalMarket market = TraditionalMarket.builder()
                .name("DongDaeMun Market")
                .address("Seoul")
                .lat(BigDecimal.valueOf(37.5700))
                .lng(BigDecimal.valueOf(127.0099))
                .build();

        marketRepository.save(market);

        // when
        List<TraditionalMarket> results = searchQueryRepository.searchTraditionalMarkets("dongdaemun");

        // then
        assertThat(results).hasSize(1);
    }

    @DisplayName("시장명 검색: 정확히 일치하는 경우 높은 점수")
    @Test
    void searchTraditionalMarkets_exactMatch_prioritized() {
        // given: 정확히 일치 + 부분 일치 혼재
        TraditionalMarket exact = TraditionalMarket.builder()
                .name("동대문시장")
                .address("서울")
                .lat(BigDecimal.valueOf(37.5700))
                .lng(BigDecimal.valueOf(127.0099))
                .build();

        TraditionalMarket partial = TraditionalMarket.builder()
                .name("동대문 안경시장")
                .address("서울")
                .lat(BigDecimal.valueOf(37.5710))
                .lng(BigDecimal.valueOf(127.0100))
                .build();

        marketRepository.saveAll(List.of(exact, partial));

        // when: "동대문시장" 검색
        List<TraditionalMarket> results = searchQueryRepository.searchTraditionalMarkets("동대문시장");

        // then: 정확히 일치하는 것이 먼저 (ID 순 정렬)
        assertThat(results)
                .hasSize(2)
                .first()
                .extracting(TraditionalMarket::getName)
                .isEqualTo("동대문시장");
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

    @DisplayName("시장명 검색: null 키워드 → 결과 없음")
    @Test
    void searchTraditionalMarkets_nullKeyword_returnEmpty() {
        // given
        TraditionalMarket market = TraditionalMarket.builder()
                .name("동대문시장")
                .address("서울")
                .lat(BigDecimal.valueOf(37.5700))
                .lng(BigDecimal.valueOf(127.0099))
                .build();

        marketRepository.save(market);

        // when
        List<TraditionalMarket> results = searchQueryRepository.searchTraditionalMarkets(null);

        // then
        assertThat(results).isEmpty();
    }

    @DisplayName("시장명 검색: 최대 200건 제한")
    @Test
    void searchTraditionalMarkets_limitedTo200() {
        // given: 201개 저장
        List<TraditionalMarket> markets = new java.util.ArrayList<>();
        for (int i = 0; i < 201; i++) {
            markets.add(TraditionalMarket.builder()
                    .name("시장" + i)
                    .address("주소" + i)
                    .lat(BigDecimal.valueOf(37.0 + i * 0.001))
                    .lng(BigDecimal.valueOf(127.0 + i * 0.001))
                    .build());
        }
        marketRepository.saveAll(markets);

        // when
        List<TraditionalMarket> results = searchQueryRepository.searchTraditionalMarkets("시장");

        // then
        assertThat(results).hasSize(200);
    }
}
