package com.chunbaetour.domain.admin.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.admin.shop.dto.response.AdminShopListResponse;
import com.chunbaetour.domain.admin.shop.dto.request.AdminShopUpdateRequest;
import com.chunbaetour.domain.admin.shop.dto.response.AdminShopDetailResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
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

/**
 * AdminShopService 단위 테스트 (KAN-203 Admin S04).
 *
 * <p>status 분기(activate/hide), CLOSED 거부, partial update, 미존재 404, 카운트 위임 검증.
 */
@ExtendWith(MockitoExtension.class)
class AdminShopServiceTest {

    private static final Long SHOP_ID = 10L;

    @Mock private ShopRepository shopRepository;
    @Mock private PlaceRepository placeRepository;
    @Mock private TraditionalMarketRepository traditionalMarketRepository;
    @InjectMocks private AdminShopService adminShopService;

    private static Shop shopWithStatus(ShopStatus status) {
        Shop shop = Shop.builder()
                .userId(1L).applicationId(1L)
                .shopName("가게").category("FOOD")
                .address("서울시 강남구").phone("02-1111-2222")
                .description("기존 소개").build();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        ReflectionTestUtils.setField(shop, "status", status);
        return shop;
    }

    @Test
    @DisplayName("getShop — 미존재 시 SHOP_NOT_FOUND")
    void getShop_notFound() {
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminShopService.getShop(SHOP_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("updateShop status=SUSPENDED → hide() 전이 + 필드 partial 반영")
    void updateShop_suspend() {
        Shop shop = shopWithStatus(ShopStatus.ACTIVE);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        AdminShopDetailResponse res = adminShopService.updateShop(
                SHOP_ID, new AdminShopUpdateRequest(ShopStatus.SUSPENDED, "수정된 소개", null, null));

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.SUSPENDED);
        assertThat(shop.getDescription()).isEqualTo("수정된 소개");
        assertThat(res.status()).isEqualTo(ShopStatus.SUSPENDED);
    }

    @Test
    @DisplayName("updateShop status=ACTIVE → activate() 복구")
    void updateShop_activate() {
        Shop shop = shopWithStatus(ShopStatus.SUSPENDED);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        adminShopService.updateShop(
                SHOP_ID, new AdminShopUpdateRequest(ShopStatus.ACTIVE, null, null, null));

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateShop status=CLOSED 직접 지정 → INVALID_INPUT_VALUE")
    void updateShop_closed_rejected() {
        Shop shop = shopWithStatus(ShopStatus.ACTIVE);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        assertThatThrownBy(() -> adminShopService.updateShop(
                SHOP_ID, new AdminShopUpdateRequest(ShopStatus.CLOSED, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("updateShop CLOSED 가게 status 전이 → SHOP_INACTIVE (activate/hide 가드)")
    void updateShop_closedShop_guard() {
        Shop shop = shopWithStatus(ShopStatus.CLOSED);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        assertThatThrownBy(() -> adminShopService.updateShop(
                SHOP_ID, new AdminShopUpdateRequest(ShopStatus.ACTIVE, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("updateShop status=null → 상태 불변, 필드만 partial")
    void updateShop_noStatus() {
        Shop shop = shopWithStatus(ShopStatus.SUSPENDED);
        given(shopRepository.findById(SHOP_ID)).willReturn(Optional.of(shop));

        adminShopService.updateShop(
                SHOP_ID, new AdminShopUpdateRequest(null, "정지 중 소개 수정", "02-9999-9999", null));

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.SUSPENDED);
        assertThat(shop.getDescription()).isEqualTo("정지 중 소개 수정");
        assertThat(shop.getPhone()).isEqualTo("02-9999-9999");
    }

    @Test
    @DisplayName("getShops — 연결 장소/시장 이름을 배치 조회로 매핑, 미연결은 null, hasNext (KAN-307)")
    void getShops_mapsLinkedNames() {
        // 가게1: place·market 연결, 가게2: 미연결
        Shop linked = shopWithStatus(ShopStatus.ACTIVE);
        ReflectionTestUtils.setField(linked, "id", 11L);
        ReflectionTestUtils.setField(linked, "placeId", 100L);
        ReflectionTestUtils.setField(linked, "traditionalMarketId", 200L);
        Shop unlinked = shopWithStatus(ShopStatus.ACTIVE);
        ReflectionTestUtils.setField(unlinked, "id", 10L);

        // size=2 → size+1=3개 조회 stub(2건만 반환 → hasNext=false). keyword/status null 경로.
        given(shopRepository.searchForAdmin(isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of(linked, unlinked));

        Place place = mock(Place.class);
        given(place.getId()).willReturn(100L);
        given(place.getName()).willReturn("경복궁");
        given(placeRepository.findAllById(anyList())).willReturn(List.of(place));

        TraditionalMarket market = mock(TraditionalMarket.class);
        given(market.getId()).willReturn(200L);
        given(market.getName()).willReturn("광장시장");
        given(traditionalMarketRepository.findAllById(anyList())).willReturn(List.of(market));

        CursorPageResponse<AdminShopListResponse> result =
                adminShopService.getShops(null, null, null, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        // 연결 가게는 이름 매핑, 미연결 가게는 id·name 모두 null
        AdminShopListResponse r1 = result.content().get(0);
        assertThat(r1.placeId()).isEqualTo(100L);
        assertThat(r1.placeName()).isEqualTo("경복궁");
        assertThat(r1.traditionalMarketName()).isEqualTo("광장시장");
        AdminShopListResponse r2 = result.content().get(1);
        assertThat(r2.placeId()).isNull();
        assertThat(r2.placeName()).isNull();
        assertThat(r2.traditionalMarketName()).isNull();
    }

    @Test
    @DisplayName("getShops — size+1개 반환 시 hasNext=true, nextCursor 발급")
    void getShops_hasNext() {
        Shop a = shopWithStatus(ShopStatus.ACTIVE);
        ReflectionTestUtils.setField(a, "id", 10L);
        Shop b = shopWithStatus(ShopStatus.ACTIVE);
        ReflectionTestUtils.setField(b, "id", 9L);
        Shop c = shopWithStatus(ShopStatus.ACTIVE);
        ReflectionTestUtils.setField(c, "id", 8L);
        // size=2 + 1개 초과 → hasNext=true, 미연결이라 이름 배치 조회는 빈 id로 스킵
        given(shopRepository.searchForAdmin(isNull(), eq(ShopStatus.ACTIVE), isNull(), any(Pageable.class)))
                .willReturn(List.of(a, b, c));

        CursorPageResponse<AdminShopListResponse> result =
                adminShopService.getShops(null, ShopStatus.ACTIVE, null, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("getTotalShops / getSuspendedShops — repository 위임")
    void countMethods_delegate() {
        given(shopRepository.count()).willReturn(42L);
        given(shopRepository.countByStatus(ShopStatus.SUSPENDED)).willReturn(7L);

        assertThat(adminShopService.getTotalShops()).isEqualTo(42L);
        assertThat(adminShopService.getSuspendedShops()).isEqualTo(7L);
    }
}
