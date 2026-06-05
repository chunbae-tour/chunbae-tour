package com.chunbaetour.domain.market.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TraditionalMarketRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TraditionalMarketRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @DisplayName("전통시장 저장 및 조회")
    @Test
    void save_and_find() {
        // given
        TraditionalMarket market = TraditionalMarket.builder()
                .name("동대문시장")
                .address("서울 중구 을지로 203")
                .lat(BigDecimal.valueOf(37.5700))
                .lng(BigDecimal.valueOf(127.0099))
                .marketType("상설장")
                .build();

        // when
        TraditionalMarket saved = repository.save(market);

        // then
        assertThat(saved.getId()).isNotNull();
        Optional<TraditionalMarket> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("동대문시장");
    }

    @DisplayName("name + address 복합 키 조회")
    @Test
    void findByNameAndAddress() {
        // given
        TraditionalMarket market = TraditionalMarket.builder()
                .name("동대문시장")
                .address("서울 중구 을지로 203")
                .lat(BigDecimal.valueOf(37.5700))
                .lng(BigDecimal.valueOf(127.0099))
                .build();

        repository.save(market);

        // when
        Optional<TraditionalMarket> found = repository.findByNameAndAddress("동대문시장", "서울 중구 을지로 203");

        // then
        assertThat(found).isPresent();
    }
}
