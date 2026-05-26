package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.MenuCreateRequest;
import com.chunbaetour.domain.shop.dto.request.MenuUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.MenuResponse;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.MenuRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private MenuRepository menuRepository;

    @InjectMocks
    private MenuService menuService;

    private static final Long USER_ID = 1L;
    private static final Long SHOP_ID = 10L;
    private static final Long MENU_ID = 100L;

    /** 실제 Shop 인스턴스 생성 — 빌더 기본값: status=ACTIVE */
    private Shop createShop() {
        return Shop.builder()
                .userId(USER_ID)
                .applicationId(1L)
                .shopName("광화문 떡볶이")
                .category("FOOD")
                .address("서울 종로구 세종대로 172")
                .build();
    }

    /** 실제 Menu 인스턴스 생성 — 빌더 기본값: isAvailable=true */
    private Menu createMenu() {
        return Menu.builder()
                .shopId(SHOP_ID)
                .name("떡볶이")
                .description("매콤달콤 떡볶이")
                .price(5000L)
                .imageUrl("https://example.com/image.jpg")
                .build();
    }

    // ── POST /merchants/me/shop/menus ──────────────────────────────────────

    @Test
    @DisplayName("메뉴 등록 — 성공")
    void createMenu_success() {
        // given
        Shop shop = createShop();
        Menu menu = createMenu();
        MenuCreateRequest request = new MenuCreateRequest("떡볶이", "매콤달콤 떡볶이", 5000L, null);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(menuRepository.save(any(Menu.class))).willReturn(menu);

        // when
        MenuResponse response = menuService.createMenu(USER_ID, request);

        // then
        assertThat(response.name()).isEqualTo("떡볶이");
        assertThat(response.price()).isEqualTo(5000L);
        assertThat(response.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("메뉴 등록 — 가게 없음 → SHOP_NOT_FOUND")
    void createMenu_shopNotFound_throws() {
        // given
        MenuCreateRequest request = new MenuCreateRequest("떡볶이", null, 5000L, null);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> menuService.createMenu(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("메뉴 등록 — SUSPENDED 가게 → SHOP_INACTIVE")
    void createMenu_shopSuspended_throws() {
        // given — 상태 제어 위해 mock 사용
        Shop shop = mock(Shop.class);
        MenuCreateRequest request = new MenuCreateRequest("떡볶이", null, 5000L, null);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(shop.getStatus()).willReturn(ShopStatus.SUSPENDED);

        // then
        assertThatThrownBy(() -> menuService.createMenu(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("메뉴 등록 — CLOSED 가게 → SHOP_INACTIVE")
    void createMenu_shopClosed_throws() {
        // given
        Shop shop = mock(Shop.class);
        MenuCreateRequest request = new MenuCreateRequest("떡볶이", null, 5000L, null);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(shop.getStatus()).willReturn(ShopStatus.CLOSED);

        // then
        assertThatThrownBy(() -> menuService.createMenu(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    // ── PATCH /merchants/me/shop/menus/{menuId} ────────────────────────────

    @Test
    @DisplayName("메뉴 수정 — 성공 (부분 수정, 실제 값 변경 검증)")
    void updateMenu_success() {
        // given — Shop mock으로 getId() SHOP_ID 고정, Menu는 실제 인스턴스로 update() 호출 검증
        Shop shop = mock(Shop.class);
        given(shop.getStatus()).willReturn(ShopStatus.ACTIVE);
        given(shop.getId()).willReturn(SHOP_ID);
        Menu menu = createMenu();
        MenuUpdateRequest request = new MenuUpdateRequest("순대국밥", null, 7000L, null, null);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findByIdAndShopId(MENU_ID, SHOP_ID)).willReturn(Optional.of(menu));

        // when
        MenuResponse response = menuService.updateMenu(USER_ID, MENU_ID, request);

        // then — 변경 필드 확인, null 필드는 기존 값 유지
        assertThat(response.name()).isEqualTo("순대국밥");
        assertThat(response.price()).isEqualTo(7000L);
        assertThat(response.description()).isEqualTo("매콤달콤 떡볶이"); // null 요청 → 기존 값 유지
    }

    @Test
    @DisplayName("메뉴 수정 — isAvailable 변경 성공")
    void updateMenu_isAvailable_success() {
        // given
        Shop shop = mock(Shop.class);
        given(shop.getStatus()).willReturn(ShopStatus.ACTIVE);
        given(shop.getId()).willReturn(SHOP_ID);
        Menu menu = createMenu();
        MenuUpdateRequest request = new MenuUpdateRequest(null, null, null, null, false);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findByIdAndShopId(MENU_ID, SHOP_ID)).willReturn(Optional.of(menu));

        // when
        MenuResponse response = menuService.updateMenu(USER_ID, MENU_ID, request);

        // then
        assertThat(response.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("메뉴 수정 — 가게 없음 → SHOP_NOT_FOUND")
    void updateMenu_shopNotFound_throws() {
        // given
        MenuUpdateRequest request = new MenuUpdateRequest("새이름", null, null, null, null);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> menuService.updateMenu(USER_ID, MENU_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("메뉴 수정 — 메뉴 없음 또는 타 가게 소속 → MENU_NOT_FOUND")
    void updateMenu_menuNotFound_throws() {
        // given
        Shop shop = mock(Shop.class);
        given(shop.getStatus()).willReturn(ShopStatus.ACTIVE);
        given(shop.getId()).willReturn(SHOP_ID);
        MenuUpdateRequest request = new MenuUpdateRequest("새이름", null, null, null, null);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findByIdAndShopId(MENU_ID, SHOP_ID)).willReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> menuService.updateMenu(USER_ID, MENU_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MENU_NOT_FOUND);
    }

    @Test
    @DisplayName("메뉴 수정 — SUSPENDED 가게 → SHOP_INACTIVE")
    void updateMenu_shopInactive_throws() {
        // given
        Shop shop = mock(Shop.class);
        MenuUpdateRequest request = new MenuUpdateRequest("새이름", null, null, null, null);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(shop.getStatus()).willReturn(ShopStatus.SUSPENDED);

        // then
        assertThatThrownBy(() -> menuService.updateMenu(USER_ID, MENU_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    // ── DELETE /merchants/me/shop/menus/{menuId} ───────────────────────────

    @Test
    @DisplayName("메뉴 삭제 — 성공")
    void deleteMenu_success() {
        // given
        Shop shop = mock(Shop.class);
        given(shop.getStatus()).willReturn(ShopStatus.ACTIVE);
        given(shop.getId()).willReturn(SHOP_ID);
        Menu menu = createMenu();
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findByIdAndShopId(MENU_ID, SHOP_ID)).willReturn(Optional.of(menu));

        // when
        menuService.deleteMenu(USER_ID, MENU_ID);

        // then — soft delete: deletedAt 설정 확인
        assertThat(menu.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("메뉴 삭제 — SUSPENDED 가게 → SHOP_INACTIVE")
    void deleteMenu_shopInactive_throws() {
        // given
        Shop shop = mock(Shop.class);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(shop.getStatus()).willReturn(ShopStatus.SUSPENDED);

        // then
        assertThatThrownBy(() -> menuService.deleteMenu(USER_ID, MENU_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("메뉴 삭제 — 가게 없음 → SHOP_NOT_FOUND")
    void deleteMenu_shopNotFound_throws() {
        // given
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> menuService.deleteMenu(USER_ID, MENU_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("메뉴 삭제 — 메뉴 없음 또는 타 가게 소속 → MENU_NOT_FOUND")
    void deleteMenu_menuNotFound_throws() {
        // given
        Shop shop = mock(Shop.class);
        given(shop.getStatus()).willReturn(ShopStatus.ACTIVE);
        given(shop.getId()).willReturn(SHOP_ID);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(menuRepository.findByIdAndShopId(MENU_ID, SHOP_ID)).willReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> menuService.deleteMenu(USER_ID, MENU_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MENU_NOT_FOUND);
    }
}
