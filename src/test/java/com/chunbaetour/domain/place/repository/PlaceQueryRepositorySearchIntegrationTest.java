package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.search.dto.response.SearchPlaceResponse;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import jakarta.persistence.EntityManager;

@Transactional
class PlaceQueryRepositorySearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlaceQueryRepository placeQueryRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("MySQL FULLTEXT 인덱스(Boolean Mode) 검색 동작 검증")
    void testFullTextSearch() {
        // given
        Place place1 = createPlace("제주 카페", 33.0, 126.0);
        Place place2 = createPlace("제주도 테마파크", 33.1, 126.1);
        Place place3 = createPlace("서울 카페", 37.0, 127.0);
        
        placeRepository.saveAll(List.of(place1, place2, place3));

        // flush & clear (영속성 컨텍스트 비우고 DB에 인덱스 적용되게 하기 위함)
        entityManager.flush();
        entityManager.clear();

        // when 1: "제주" 검색 (부분 일치: "제주도"도 검색되어야 함)
        List<SearchPlaceResponse> result1 = placeQueryRepository.searchByKeyword("제주", null, null, null, 10);
        // then 1
        assertThat(result1).hasSize(2);
        assertThat(result1).extracting("name").containsExactlyInAnyOrder("제주 카페", "제주도 테마파크");

        // when 2: "+제주" 특수문자 포함 검색 (정규식 파서에 의해 "제주"로 정제됨)
        List<SearchPlaceResponse> result2 = placeQueryRepository.searchByKeyword("+제주", null, null, null, 10);
        // then 2
        assertThat(result2).hasSize(2);

        // when 3: "제주 카페" 멀티 토큰 검색 (두 단어 모두 포함된 place1만 반환되어야 함)
        List<SearchPlaceResponse> result3 = placeQueryRepository.searchByKeyword("제주 카페", null, null, null, 10);
        // then 3
        assertThat(result3).hasSize(1);
        assertThat(result3.get(0).name()).isEqualTo("제주 카페");
        
        // when 4: "++" 특수문자 전용 검색 (Fallback LIKE 로직 동작)
        List<SearchPlaceResponse> result4 = placeQueryRepository.searchByKeyword("++", null, null, null, 10);
        // then 4
        assertThat(result4).isEmpty();
        
        // when 5: 1글자 "제" 검색 (Fallback LIKE 로직 동작)
        List<SearchPlaceResponse> result5 = placeQueryRepository.searchByKeyword("제", null, null, null, 10);
        // then 5
        assertThat(result5).hasSize(2);
    }

    private Place createPlace(String name, double lat, double lng) {
        return Place.builder()
                .name(name)
                .category(PlaceCategory.TOURIST_SPOT)
                .address("테스트 주소")
                .lat(BigDecimal.valueOf(lat))
                .lng(BigDecimal.valueOf(lng))
                .build();
    }
}
