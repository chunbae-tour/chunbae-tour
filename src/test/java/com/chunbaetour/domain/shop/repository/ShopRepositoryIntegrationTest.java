package com.chunbaetour.domain.shop.repository;

import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShopRepository 통합 테스트")
class ShopRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShopRepository shopRepository;

    @Test
    @DisplayName("주변 상점 조회 (findNearbyShops) - 정상 동작 검증")
    void testFindNearbyShops() {
        // given
        // 기준 위도: 37.5, 기준 경도: 127.0
        double baseLat = 37.5;
        double baseLng = 127.0;

        // 1. 기준점 근처 ACTIVE 상점 (0.5km 이내)
        Shop nearShop1 = createTestShop(1L, "가까운상점1", "식당", "주소1", 37.502, 127.002, ShopStatus.ACTIVE);
        
        // 2. 기준점 근처 ACTIVE 상점 (1km 이내, nearShop1보다 멂)
        Shop nearShop2 = createTestShop(2L, "가까운상점2", "카페", "주소2", 37.504, 127.004, ShopStatus.ACTIVE);

        // 3. 기준점에서 아주 먼 상점 (10km 밖) - 반경 5km 테스트 시 제외되어야 함
        Shop farShop = createTestShop(3L, "먼상점", "식당", "주소3", 37.6, 127.1, ShopStatus.ACTIVE);

        // 4. 기준점 근처지만 비활성(SUSPENDED) 상점
        Shop inactiveShop = createTestShop(4L, "정지상점", "식당", "주소4", 37.501, 127.001, ShopStatus.SUSPENDED);

        // 5. 좌표가 NULL인 상점
        Shop nullCoordShop = createTestShop(5L, "좌표없는상점", "식당", "주소5", null, null, ShopStatus.ACTIVE);

        shopRepository.saveAll(List.of(nearShop1, nearShop2, farShop, inactiveShop, nullCoordShop));

        // when (반경 5km, 최대 2개)
        List<Shop> result = shopRepository.findNearbyShops(baseLat, baseLng, 5.0, 2);

        // then
        // ACTIVE + 좌표 존재 + 5km 이내 조건 충족하는 것은 nearShop1, nearShop2 두 곳
        assertThat(result).hasSize(2);
        
        // 거리순으로 가장 가까운 nearShop1이 첫 번째로 나와야 함
        assertThat(result.get(0).getShopName()).isEqualTo("가까운상점1");
        assertThat(result.get(1).getShopName()).isEqualTo("가까운상점2");
    }

    private Shop createTestShop(Long id, String name, String category, String address, Double lat, Double lng, ShopStatus status) {
        Shop shop = Shop.builder()
                .userId(id)
                .applicationId(id)
                .shopName(name)
                .category(category)
                .address(address)
                .lat(lat != null ? BigDecimal.valueOf(lat) : null)
                .lng(lng != null ? BigDecimal.valueOf(lng) : null)
                .description("테스트 상점 설명")
                .build();
        
        shop.changeStatus(status);
        return shop;
    }
}
