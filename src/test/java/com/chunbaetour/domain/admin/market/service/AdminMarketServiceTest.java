package com.chunbaetour.domain.admin.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chunbaetour.domain.admin.market.dto.response.AdminMarketDetailResponse;
import com.chunbaetour.domain.admin.market.dto.response.AdminMarketListResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminMarketServiceTest {

    @Mock private TraditionalMarketRepository traditionalMarketRepository;
    @InjectMocks private AdminMarketService adminMarketService;

    private static TraditionalMarket market(Long id, String name) {
        TraditionalMarket m = TraditionalMarket.builder()
                .name(name).address("서울특별시 종로구 창경궁로 88")
                .lat(new BigDecimal("37.5701000")).lng(new BigDecimal("126.9997000"))
                .marketType("상설장").sido("서울특별시").sigungu("종로구").build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    @DisplayName("getMarkets — 요약 매핑 + size 요청값 echo(마지막 페이지) (KAN-308)")
    void getMarkets_maps_and_echoesRequestedSize() {
        given(traditionalMarketRepository.searchForAdmin(isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of(market(2L, "광장시장"), market(1L, "남대문시장")));

        // size=5 요청, 2건 반환 → size 필드는 반환 개수(2)가 아니라 요청 size(5) echo(팀 표준)
        CursorPageResponse<AdminMarketListResponse> result =
                adminMarketService.getMarkets(null, null, null, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.content().get(0).name()).isEqualTo("광장시장");
        assertThat(result.content().get(0).marketType()).isEqualTo("상설장");
        assertThat(result.content().get(0).sido()).isEqualTo("서울특별시");
    }

    @Test
    @DisplayName("getMarkets — sido 필터 전달 + size+1개 반환 시 hasNext=true, nextCursor 발급")
    void getMarkets_sidoFilter_hasNext() {
        given(traditionalMarketRepository.searchForAdmin(isNull(), eq("서울특별시"), isNull(), any(Pageable.class)))
                .willReturn(List.of(market(3L, "A"), market(2L, "B"), market(1L, "C")));

        CursorPageResponse<AdminMarketListResponse> result =
                adminMarketService.getMarkets(null, "서울특별시", null, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
        then(traditionalMarketRepository).should()
                .searchForAdmin(isNull(), eq("서울특별시"), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("getMarket — 존재 시 상세 반환")
    void getMarket_found() {
        given(traditionalMarketRepository.findById(10L)).willReturn(Optional.of(market(10L, "동대문시장")));

        AdminMarketDetailResponse res = adminMarketService.getMarket(10L);

        assertThat(res.id()).isEqualTo(10L);
        assertThat(res.name()).isEqualTo("동대문시장");
        assertThat(res.lat()).isEqualByComparingTo("37.5701000");
    }

    @Test
    @DisplayName("getMarket — 미존재 → MARKET_NOT_FOUND")
    void getMarket_notFound() {
        given(traditionalMarketRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminMarketService.getMarket(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MARKET_NOT_FOUND);
    }
}
