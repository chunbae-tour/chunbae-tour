package com.chunbaetour.domain.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.common.config.PublicDataApiProperties;
import com.chunbaetour.domain.market.dto.response.MarketApiItem;
import com.chunbaetour.domain.market.service.MarketSyncBatchService.UpsertResult;
import java.util.List;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class MarketSyncServiceTest {

    @Mock
    private MarketSyncBatchService batchService;

    private MarketSyncService marketSyncService;

    @BeforeEach
    void setUp() {
        marketSyncService = new MarketSyncService(
                mock(RestClient.class),
                mock(PublicDataApiProperties.class),
                mock(LockProvider.class),
                batchService
        );
    }

    private MarketApiItem item(String name) {
        MarketApiItem item = new MarketApiItem();
        item.mrktNm = name;
        item.rdnmadr = "서울시 종로구 " + name + "로 1";
        item.latitude = "37.5700";
        item.longitude = "126.9790";
        return item;
    }

    @Test
    @DisplayName("DB 제약 위반 발생 시 해당 아이템 SKIPPED 처리 후 나머지 배치 계속 진행")
    void processBatch_dataIntegrityViolation_skipsItemAndContinues() {
        MarketApiItem good1 = item("정상시장A");
        MarketApiItem bad = item("중복시장");
        MarketApiItem good2 = item("정상시장B");

        given(batchService.upsertItem(good1)).willReturn(UpsertResult.INSERTED);
        given(batchService.upsertItem(bad)).willThrow(new DataIntegrityViolationException("uk_traditional_markets_name_address"));
        given(batchService.upsertItem(good2)).willReturn(UpsertResult.UPDATED);

        MarketSyncService.SyncResult result = marketSyncService.processBatch(List.of(good1, bad, good2));

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("모든 아이템 정상 처리 시 SKIPPED 없음")
    void processBatch_allSuccess_noSkipped() {
        MarketApiItem a = item("시장A");
        MarketApiItem b = item("시장B");

        given(batchService.upsertItem(a)).willReturn(UpsertResult.INSERTED);
        given(batchService.upsertItem(b)).willReturn(UpsertResult.UPDATED);

        MarketSyncService.SyncResult result = marketSyncService.processBatch(List.of(a, b));

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
    }

    @Test
    @DisplayName("DB 인프라 장애(DataAccessException) 발생 시 배치 전체 중단 — catch(Exception) 회귀 방지")
    void processBatch_dataAccessException_propagates() {
        MarketApiItem bad = item("장애시장");
        given(batchService.upsertItem(bad)).willThrow(new QueryTimeoutException("DB timeout"));

        assertThatThrownBy(() -> marketSyncService.processBatch(List.of(bad)))
                .isInstanceOf(QueryTimeoutException.class);
    }

    @Test
    @DisplayName("null 아이템은 skipped 처리 후 배치 계속 진행")
    void processBatch_nullItem_skipsAndContinues() {
        MarketApiItem good = item("정상시장");
        given(batchService.upsertItem(good)).willReturn(UpsertResult.INSERTED);

        MarketSyncService.SyncResult result = marketSyncService.processBatch(
                new java.util.ArrayList<>(java.util.Arrays.asList(null, good)));

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 목록 처리 시 0건 반환")
    void processBatch_emptyList_returnsZero() {
        MarketSyncService.SyncResult result = marketSyncService.processBatch(List.of());

        assertThat(result.inserted()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isZero();
    }
}
