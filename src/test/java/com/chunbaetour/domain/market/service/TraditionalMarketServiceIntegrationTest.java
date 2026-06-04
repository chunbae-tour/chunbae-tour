package com.chunbaetour.domain.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.market.dto.response.TraditionalMarketNearbyResponse;
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

    @DisplayName("위치 기반 조회: 기준점(0,0) 반경 3km 내 시장 조회")
    @Test
    void findNearby_withinRadius() {
        // given: 기준점(0,0)에서 반경 3km
        TraditionalMarket market1 = TraditionalMarket.builder()
                .name("시장1")
                .address("서울")
                .lat(BigDecimal.valueOf(37.5700))  // 약 6.4km
                .lng(BigDecimal.valueOf(127.0099))
                .marketType("상설장")
                .build();

        TraditionalMarket market2 = TraditionalMarket.builder()
                .name("시장2")
                .address("서울")
                .lat(BigDecimal.valueOf(37.0300))  // 약 50km
                .lng(BigDecimal.valueOf(127.0099))
                .marketType("상설장")
                .build();

        marketRepository.saveAll(List.of(market1, market2));

        // when: 기준점 (37.5500, 127.0000)에서 반경 1000m (1km)
        CursorPageResponse<TraditionalMarketNearbyResponse> result = service.findNearby(
                BigDecimal.valueOf(37.5500),
                BigDecimal.valueOf(127.0000),
                1000,
                null,
                10
        );

        // then: 가까운 시장 반환
        assertThat(result.content()).isNotEmpty();
    }

    @DisplayName("위치 기반 조회: 커서 페이지네이션")
    @Test
    void findNearby_withCursor() {
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

        // when: 첫 페이지 (size=1)
        CursorPageResponse<TraditionalMarketNearbyResponse> page1 = service.findNearby(
                BigDecimal.valueOf(37.5500),
                BigDecimal.valueOf(127.0000),
                10000,
                null,
                1
        );

        // then: hasNext=true, nextCursor 있음
        assertThat(page1.content()).hasSize(1);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.nextCursor()).isNotNull();
    }
}
