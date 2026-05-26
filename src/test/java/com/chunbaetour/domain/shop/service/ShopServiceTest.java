package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.ShopUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.ShopResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ShopService shopService;

    private static final Long USER_ID = 1L;

    /** 실제 Shop 인스턴스 생성 — 빌더 기본값: status=ACTIVE */
    private Shop createShop() {
        return Shop.builder()
                .userId(USER_ID)
                .applicationId(1L)
                .shopName("광화문 떡볶이")
                .category("FOOD")
                .address("서울 종로구 세종대로 172")
                .phone("02-1234-5678")
                .description("전통 떡볶이 전문점")
                .build();
    }

    // ── GET /merchants/me/shop ──────────────────────────────────────────────

    @Test
    @DisplayName("내 가게 조회 — 성공")
    void getMyShop_success() {
        // given — 실제 Shop 인스턴스 사용 (ACTIVE 상태)
        Shop shop = createShop();
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));

        // when
        ShopResponse response = shopService.getMyShop(USER_ID);

        // then
        assertThat(response.shopName()).isEqualTo("광화문 떡볶이");
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(ShopStatus.ACTIVE);
    }

    @Test
    @DisplayName("내 가게 조회 — 가게 없음 → SHOP_NOT_FOUND")
    void getMyShop_notFound_throws() {
        // given — userId에 해당하는 가게 없음
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> shopService.getMyShop(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    // ── PATCH /merchants/me/shop ────────────────────────────────────────────

    @Test
    @DisplayName("내 가게 수정 — 성공 (부분 수정, 실제 값 변경 검증)")
    void updateMyShop_success() {
        // given — 실제 Shop 인스턴스로 update() 실제 호출 검증
        Shop shop = createShop();
        ShopUpdateRequest request = new ShopUpdateRequest(
                "새로운 가게명", null, "02-9999-8888", "업데이트된 소개글", null, null, null
        );
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));

        // when
        ShopResponse response = shopService.updateMyShop(USER_ID, request);

        // then — update() 실제 호출 후 변경된 값 검증 (null 필드는 기존 값 유지 확인)
        assertThat(response.shopName()).isEqualTo("새로운 가게명");
        assertThat(response.phone()).isEqualTo("02-9999-8888");
        assertThat(response.description()).isEqualTo("업데이트된 소개글");
        assertThat(response.category()).isEqualTo("FOOD"); // null 요청 → 기존 값 유지
    }

    @Test
    @DisplayName("내 가게 수정 — SUSPENDED 상태 → SHOP_INACTIVE")
    void updateMyShop_suspended_throws() {
        // given — 상태 제어가 필요해 mock 사용 (빌더로 SUSPENDED 생성 불가)
        Shop shop = mock(Shop.class);
        ShopUpdateRequest request = new ShopUpdateRequest("새이름", null, null, null, null, null, null);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(shop.getStatus()).willReturn(ShopStatus.SUSPENDED);

        // then
        assertThatThrownBy(() -> shopService.updateMyShop(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("내 가게 수정 — CLOSED 상태 → SHOP_INACTIVE")
    void updateMyShop_closed_throws() {
        // given — 폐업 가게는 수정 불가
        Shop shop = mock(Shop.class);
        ShopUpdateRequest request = new ShopUpdateRequest("새이름", null, null, null, null, null, null);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(shop.getStatus()).willReturn(ShopStatus.CLOSED);

        // then
        assertThatThrownBy(() -> shopService.updateMyShop(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("내 가게 수정 — 가게 없음 → SHOP_NOT_FOUND")
    void updateMyShop_notFound_throws() {
        // given — userId에 해당하는 가게 없음
        ShopUpdateRequest request = new ShopUpdateRequest(null, null, null, null, null, null, null);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> shopService.updateMyShop(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }
}
