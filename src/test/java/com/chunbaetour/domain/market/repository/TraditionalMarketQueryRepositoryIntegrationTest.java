package com.chunbaetour.domain.market.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyResponse;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TraditionalMarketQueryRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TraditionalMarketRepository repository;

    @Autowired
    private TraditionalMarketQueryRepository queryRepository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @DisplayName("위치 기반 조회: 반경 내 시장만 반환")
    @Test
    void findNearby_basic() {
        // given: 기준점(37.57, 127.01) 근처 2개 시장
        TraditionalMarket market1 = TraditionalMarket.builder()
                .name("시장1")
                .address("서울")
                .lat(BigDecimal.valueOf(37.5700))
                .lng(BigDecimal.valueOf(127.0100))
                .marketType("상설장")
                .build();

        TraditionalMarket market2 = TraditionalMarket.builder()
                .name("시장2")
                .address("서울")
                .lat(BigDecimal.valueOf(37.0000))  // 멀리 떨어짐
                .lng(BigDecimal.valueOf(127.0000))
                .marketType("상설장")
                .build();

        repository.saveAll(List.of(market1, market2));

        // when: 기준점에서 반경 1km 내
        List<TraditionalMarketNearbyResponse> results = queryRepository.findNearby(
                37.5700, 127.0100,
                1000,  // 반경 1km
                0L, 10
        );

        // then: 근처 시장만 반환
        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("시장1");
    }
}
