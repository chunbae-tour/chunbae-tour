package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chunbaetour.domain.support.AbstractIntegrationTest;

@SpringBootTest
class PlaceStatsSyncBatchServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PlaceStatsSyncBatchService placeStatsSyncBatchService;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Place testPlace1;
    private Place testPlace2;

    @BeforeEach
    void setUp() {
        // DB 초기화
        testPlace1 = Place.createFromApi(
                "ext-1", "테스트 장소 1", "주소 1",
                BigDecimal.valueOf(37.1), BigDecimal.valueOf(127.1),
                null, null, "서울", "강남구"
        );
        testPlace2 = Place.createFromApi(
                "ext-2", "테스트 장소 2", "주소 2",
                BigDecimal.valueOf(37.2), BigDecimal.valueOf(127.2),
                null, null, "서울", "서초구"
        );

        placeRepository.save(testPlace1);
        placeRepository.save(testPlace2);
    }

    @AfterEach
    void tearDown() {
        placeRepository.deleteAllInBatch();
        stringRedisTemplate.delete(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY);
        stringRedisTemplate.delete(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + testPlace1.getId());
        stringRedisTemplate.delete(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + testPlace2.getId());
        stringRedisTemplate.delete(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + testPlace1.getId());
        stringRedisTemplate.delete(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + testPlace2.getId());
    }

    @Test
    @DisplayName("더티 마킹된 조회수/좋아요 수가 DB에 동기화되어야 한다")
    void syncDirtyStats() {
        // given
        // Redis에 테스트 값 적재
        stringRedisTemplate.opsForValue().set(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + testPlace1.getId(), "100");
        stringRedisTemplate.opsForValue().set(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + testPlace1.getId(), "50");
        
        stringRedisTemplate.opsForValue().set(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + testPlace2.getId(), "200");
        // testPlace2는 좋아요 수 없음 (단건 변경 테스트)

        // 더티 마킹
        stringRedisTemplate.opsForSet().add(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, String.valueOf(testPlace1.getId()));
        stringRedisTemplate.opsForSet().add(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, String.valueOf(testPlace2.getId()));

        // when
        placeStatsSyncBatchService.syncDirtyStats();

        // then
        // 더티 큐가 비워졌는지 확인
        Long size = stringRedisTemplate.opsForSet().size(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY);
        assertThat(size).isZero();

        // DB에 값이 반영되었는지 확인
        Place updatedPlace1 = placeRepository.findById(testPlace1.getId()).orElseThrow();
        assertThat(updatedPlace1.getViewCount()).isEqualTo(100);
        assertThat(updatedPlace1.getLikeCount()).isEqualTo(50);

        Place updatedPlace2 = placeRepository.findById(testPlace2.getId()).orElseThrow();
        assertThat(updatedPlace2.getViewCount()).isEqualTo(200);
        // likeCount는 기존 값(0) 유지되어야 함
        assertThat(updatedPlace2.getLikeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("어드민 수동 변경 등으로 DB값이 크더라도, 배치가 돌면 Redis의 현재 값으로 강제 덮어씌워져야 한다 (GREATEST 제거됨)")
    void syncDirtyStats_overwriteDbValue() {
        // given
        // JdbcTemplate을 통해 DB의 기존 조회수/좋아요를 크게 설정
        jdbcTemplate.update("UPDATE places SET view_count = 500, like_count = 300 WHERE id = ?", testPlace1.getId());

        // Redis에는 어드민 수정 이후 들어온 작은 값이라 가정
        stringRedisTemplate.opsForValue().set(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + testPlace1.getId(), "10");

        // 더티 마킹
        stringRedisTemplate.opsForSet().add(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, String.valueOf(testPlace1.getId()));

        // when
        placeStatsSyncBatchService.syncDirtyStats();

        // then
        Place updatedPlace = placeRepository.findById(testPlace1.getId()).orElseThrow();
        // Redis 값이 DB보다 작더라도 GREATEST가 제거되었으므로 덮어씌워져야 함
        assertThat(updatedPlace.getViewCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("찜 취소 등으로 Redis의 좋아요 수가 감소했을 때 DB에 정상적으로 반영되어야 한다")
    void syncDirtyStats_allowLikeDecrease() {
        // given
        jdbcTemplate.update("UPDATE places SET like_count = 100 WHERE id = ?", testPlace1.getId());

        // 사용자가 찜을 취소하여 Redis의 값이 DB보다 작아짐
        stringRedisTemplate.opsForValue().set(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + testPlace1.getId(), "99");

        // 더티 마킹
        stringRedisTemplate.opsForSet().add(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, String.valueOf(testPlace1.getId()));

        // when
        placeStatsSyncBatchService.syncDirtyStats();

        // then
        Place updatedPlace = placeRepository.findById(testPlace1.getId()).orElseThrow();
        // 좋아요 수는 GREATEST 제약이 없으므로 더 작은 값으로 덮어써져야 함
        assertThat(updatedPlace.getLikeCount()).isEqualTo(99);
    }
}
