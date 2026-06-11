package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.like.service.UserLikeService;
import com.chunbaetour.domain.like.type.LikeTargetType;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.search.dto.response.SearchPlaceResponse;
import com.chunbaetour.domain.search.dto.response.TypoCorrectedSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.AfterEach;

import com.chunbaetour.domain.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SearchPlacePersonalizationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserLikeService userLikeService;

    @Autowired
    private com.chunbaetour.domain.like.repository.UserLikeRepository userLikeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testUserId;

    @BeforeEach
    void setUpFullTextIndex() {
        try {
            jdbcTemplate.execute("CREATE FULLTEXT INDEX idx_places_name_fulltext ON places(name) WITH PARSER ngram");
        } catch (Exception e) {
            if (e.getMessage() == null || (!e.getMessage().contains("Duplicate key name") && !e.getMessage().contains("already exists"))) {
                throw e;
            }
        }
    }

    @AfterEach
    void tearDown() {
        userLikeRepository.deleteAll();
        placeRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        // 1. 유저 생성
        Account account = Account.registerUser("testuser@example.com", "password", "nickname");
        Account savedAccount = accountRepository.save(account);
        testUserId = savedAccount.getId();

        // 2. 관광지 데이터 세팅 (이름에 모두 "통합테스트" 포함, 총 5개)

        // ID가 늦게 삽입될수록 높다고 가정하면 (실제로는 sequence지만),
        // 최신순(id desc) 정렬 시 아래 삽입 순서의 역순으로 조회됨.
        Place place1 = Place.builder().name("통합테스트 장소1").category(PlaceCategory.TRADITIONAL_MARKET).address("주소1").lat(BigDecimal.valueOf(37.5)).lng(BigDecimal.valueOf(126.9)).build();
        Place place2 = Place.builder().name("통합테스트 장소2").category(PlaceCategory.TOURIST_SPOT).address("주소2").lat(BigDecimal.valueOf(37.5)).lng(BigDecimal.valueOf(126.9)).build();
        Place place3 = Place.builder().name("통합테스트 장소3").category(PlaceCategory.TRADITIONAL_MARKET).address("주소3").lat(BigDecimal.valueOf(37.5)).lng(BigDecimal.valueOf(126.9)).build();
        Place place4 = Place.builder().name("통합테스트 장소4").category(PlaceCategory.TRADITIONAL_MARKET).address("주소4").lat(BigDecimal.valueOf(37.5)).lng(BigDecimal.valueOf(126.9)).build();
        Place place5 = Place.builder().name("통합테스트 장소5").category(PlaceCategory.TOURIST_SPOT).address("주소5").lat(BigDecimal.valueOf(37.5)).lng(BigDecimal.valueOf(126.9)).build();

        placeRepository.saveAll(List.of(place1, place2, place3, place4, place5));

        // 3. 선호 카테고리 만들기 (유저가 TOURIST_SPOT 장소 2개를 찜함)
        userLikeService.addLike(testUserId, LikeTargetType.PLACE, place2.getId());
        userLikeService.addLike(testUserId, LikeTargetType.PLACE, place5.getId());
    }

    @Test
    @DisplayName("In-memory Boost가 커서 무결성을 해치지 않고 1, 2페이지 모두 정상 노출한다")
    void searchPlaces_WithInMemoryBoost_MaintainsCursorIntegrity() {
        // [1페이지 조회] size = 3
        TypoCorrectedSearchResponse<SearchPlaceResponse> page1 = searchService.searchPlaces(
                "통합테스트", null, null, null, 3, "127.0.0.1", null, testUserId);

        List<SearchPlaceResponse> content1 = page1.content();
        assertThat(content1).hasSize(3);
        assertThat(page1.hasNext()).isTrue();

        // 1페이지에 노출된 장소 ID들을 수집
        List<Long> page1Ids = content1.stream().map(SearchPlaceResponse::placeId).toList();
        
        // boost 1개(place5)는 무조건 첫 번째 페이지 상단에 노출되거나 섞여 있어야 한다. 
        // 만약 place5가 안 나왔더라도 무조건 중복 없이 3개가 나와야 함.

        // [2페이지 조회] 
        Long cursor = Long.valueOf(page1.nextCursor());
        TypoCorrectedSearchResponse<SearchPlaceResponse> page2 = searchService.searchPlaces(
                "통합테스트", null, null, cursor, 3, "127.0.0.1", null, testUserId);

        List<SearchPlaceResponse> content2 = page2.content();
        
        // 데이터가 5개였으므로, 2페이지는 2개만 반환되어야 함.
        assertThat(content2).hasSize(2);
        assertThat(page2.hasNext()).isFalse();

        // 2페이지에 노출된 장소 ID들을 수집
        List<Long> page2Ids = content2.stream().map(SearchPlaceResponse::placeId).toList();

        // 핵심 검증: 1페이지와 2페이지의 결과 ID들이 겹치지 않아야 하며, 총 5개가 모두 나와야 한다 (누락 없음 보장)
        assertThat(page1Ids).doesNotContainAnyElementsOf(page2Ids);
        assertThat(page1Ids.size() + page2Ids.size()).isEqualTo(5);
    }
}
