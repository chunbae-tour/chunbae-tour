package com.chunbaetour.domain.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyPageResponse;
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
class TraditionalMarketServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TraditionalMarketService service;

    @Autowired
    private TraditionalMarketRepository marketRepository;

    @AfterEach
    void cleanup() {
        marketRepository.deleteAll();
    }

    @DisplayName("위치 기반 조회: 반경 내 시장만 반환")
    @Test
    void findNearby_withinRadius() {
        // given: 기준점 (37.5500, 127.0000) 근처 시장 2개
        TraditionalMarket market1 = TraditionalMarket.builder()
                .name("시장1_가까운곳")
                .address("서울")
                .lat(BigDecimal.valueOf(37.55001))
                .lng(BigDecimal.valueOf(127.00001))
                .marketType("상설장")
                .build();

        TraditionalMarket market2 = TraditionalMarket.builder()
                .name("시장2_먼곳")
                .address("경기")
                .lat(BigDecimal.valueOf(37.0000))
                .lng(BigDecimal.valueOf(127.0000))
                .marketType("상설장")
                .build();

        marketRepository.saveAll(List.of(market1, market2));

        // when: 기준점에서 반경 1km, 첫 페이지
        TraditionalMarketNearbyPageResponse result = service.findNearby(
                BigDecimal.valueOf(37.5500),
                BigDecimal.valueOf(127.0000),
                1000,
                0,
                10
        );

        // then: 가까운 시장만 반환
        assertThat(result.markets()).hasSize(1);
        assertThat(result.markets().get(0).name()).isEqualTo("시장1_가까운곳");
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.hasNext()).isFalse();
    }

    @DisplayName("위치 기반 조회: page 기반 페이지네이션")
    @Test
    void findNearby_withPagination() {
        // given: 3개 시장
        for (int i = 0; i < 3; i++) {
            TraditionalMarket market = TraditionalMarket.builder()
                    .name("시장" + i)
                    .address("서울")
                    .lat(BigDecimal.valueOf(37.5500 + i * 0.001))
                    .lng(BigDecimal.valueOf(127.0000))
                    .marketType("상설장")
                    .build();
            marketRepository.save(market);
        }

        // when: 첫 페이지 (size=2)
        TraditionalMarketNearbyPageResponse page1 = service.findNearby(
                BigDecimal.valueOf(37.5500),
                BigDecimal.valueOf(127.0000),
                10000,
                0,
                2
        );

        // then: hasNext=true, 2개 반환
        assertThat(page1.markets()).hasSize(2);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.page()).isEqualTo(0);

        // when: 두 번째 페이지
        TraditionalMarketNearbyPageResponse page2 = service.findNearby(
                BigDecimal.valueOf(37.5500),
                BigDecimal.valueOf(127.0000),
                10000,
                1,
                2
        );

        // then: 나머지 1개, hasNext=false
        assertThat(page2.markets()).hasSize(1);
        assertThat(page2.hasNext()).isFalse();
    }
}
