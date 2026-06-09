package com.chunbaetour.domain.place.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.place.client.TourApiPlaceItem;
import com.chunbaetour.domain.place.dto.response.NearbyPlaceResponse;
import com.chunbaetour.domain.place.service.PlaceSyncBatchService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link PlaceQueryRepository#findNearbyPlaces} 회귀 테스트 (KAN-263).
 *
 * <p>이 쿼리는 QueryDSL이 생성하는 HQL이라 컴파일이 아닌 런타임에만 검증된다. MySQL 공간함수
 * {@code MBRContains}(0/1 반환)를 boolean 술어로 잘못 쓰면 Hibernate HQL 시맨틱 검사에서 실패해
 * 엔드포인트가 전면 500이 되지만 mock 단위 테스트로는 잡히지 않는다(실 SQL 실행 안 함).
 * 따라서 Testcontainers MySQL로 실제 쿼리를 실행해 재발을 방지한다.
 */
@SpringBootTest
class PlaceQueryRepositoryNearbyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlaceQueryRepository placeQueryRepository;

    @Autowired
    private PlaceSyncBatchService batchService;

    @Autowired
    private PlaceRepository placeRepository;

    @AfterEach
    void cleanup() {
        placeRepository.deleteAll();
    }

    // mapX=경도(lng), mapY=위도(lat)
    private TourApiPlaceItem item(String contentId, String title, String mapX, String mapY) {
        return new TourApiPlaceItem(contentId, title, "충청남도 천안시 동남구", "", mapX, mapY,
                null, null, null, "20251124134437", "1", "34");
    }

    @Test
    @DisplayName("findNearbyPlaces가 HQL 오류 없이 실행되어 반경 내 관광지만 거리순으로 반환한다")
    void findNearbyPlaces_executesAndFiltersByRadius() {
        // 기준점(천안)과 동일 좌표 1건 + 반경 밖(서울, 약 80km) 1건 저장
        batchService.upsertItem(item("1", "독립기념관", "127.2152", "36.7853"));
        batchService.upsertItem(item("2", "서울N타워", "126.9882", "37.5512"));

        double lat = 36.7853;
        double lng = 127.2152;

        // 핵심: 쿼리가 SemanticException 없이 실행되어야 한다(MBRContains 술어 회귀 가드).
        List<NearbyPlaceResponse> result =
                placeQueryRepository.findNearbyPlaces(lat, lng, 5000, null, null, 10);

        // 반경 5km 안의 독립기념관만 반환, 서울은 MBR/거리 필터로 제외
        assertThat(result).extracting(NearbyPlaceResponse::name).containsExactly("독립기념관");
        assertThat(result.get(0).distanceMeters()).isLessThanOrEqualTo(5000);
    }
}
