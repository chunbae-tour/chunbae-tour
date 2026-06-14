package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

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
        clearPlaceStatsRedisKeys();

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
        clearPlaceStatsRedisKeys();
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
    @DisplayName("DB 조회수가 Redis 값보다 크면 GREATEST 정책으로 기존 DB 값을 보존해야 한다")
    void syncDirtyStats_preserveGreaterDbViewCount() {
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
        // viewCount는 감소가 없는 누적 지표이므로 Redis 값이 낮아도 DB의 더 큰 값을 보존한다.
        assertThat(updatedPlace.getViewCount()).isEqualTo(500);
    }

    @Test
    @DisplayName("Redis 조회수가 DB 값보다 크면 DB에 증가분이 반영되어야 한다")
    void syncDirtyStats_applyGreaterRedisViewCount() {
        // given
        jdbcTemplate.update("UPDATE places SET view_count = 10 WHERE id = ?", testPlace1.getId());
        stringRedisTemplate.opsForValue().set(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + testPlace1.getId(), "500");
        stringRedisTemplate.opsForSet().add(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, String.valueOf(testPlace1.getId()));

        // when
        placeStatsSyncBatchService.syncDirtyStats();

        // then
        Place updatedPlace = placeRepository.findById(testPlace1.getId()).orElseThrow();
        assertThat(updatedPlace.getViewCount()).isEqualTo(500);
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

    @Test
    @DisplayName("잘못된 dirty ID가 있어도 유효한 ID는 동기화하고 잘못된 항목은 제거해야 한다")
    void syncDirtyStats_invalidDirtyIdDoesNotBlockValidIds() {
        // given
        String validId = String.valueOf(testPlace1.getId());
        String invalidId = "not-a-place-id";
        stringRedisTemplate.opsForValue().set(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + validId, "123");
        stringRedisTemplate.opsForSet().add(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, validId, invalidId);

        // when
        placeStatsSyncBatchService.syncDirtyStats();

        // then
        Place updatedPlace = placeRepository.findById(testPlace1.getId()).orElseThrow();
        assertThat(updatedPlace.getViewCount()).isEqualTo(123);
        assertThat(stringRedisTemplate.opsForSet().isMember(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, invalidId))
                .isFalse();
    }

    @Test
    @DisplayName("dirty ID가 100개를 초과해도 여러 청크로 나누어 모두 동기화해야 한다")
    void syncDirtyStats_processesMoreThanOneChunk() {
        // given
        List<Place> places = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            places.add(createPlace("bulk-ext-" + i, "대량 테스트 장소 " + i));
        }
        placeRepository.saveAll(places);

        for (int i = 0; i < places.size(); i++) {
            String id = String.valueOf(places.get(i).getId());
            stringRedisTemplate.opsForValue().set(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + id, String.valueOf(i + 1));
            stringRedisTemplate.opsForSet().add(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY, id);
        }

        // when
        placeStatsSyncBatchService.syncDirtyStats();

        // then
        for (int i = 0; i < places.size(); i++) {
            Place updatedPlace = placeRepository.findById(places.get(i).getId()).orElseThrow();
            assertThat(updatedPlace.getViewCount()).isEqualTo(i + 1);
        }
        assertThat(stringRedisTemplate.opsForSet().size(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY)).isZero();
    }

    private Place createPlace(String externalId, String name) {
        return Place.createFromApi(
                externalId, name, "주소 " + externalId,
                BigDecimal.valueOf(37.3), BigDecimal.valueOf(127.3),
                null, null, "서울", "중구"
        );
    }

    private void clearPlaceStatsRedisKeys() {
        deleteByPattern(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + "*");
        deleteByPattern(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + "*");
        stringRedisTemplate.delete(PlaceRedisConstants.PLACE_DIRTY_STATS_KEY);
    }

    private void deleteByPattern(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (var cursor = stringRedisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @TestConfiguration
    static class NoAutoSchedulingConfig {

        @Bean
        @Primary
        LockProvider noOpLockProvider() {
            return lockConfiguration -> java.util.Optional.of(() -> {});
        }

        @Bean
        @Primary
        TaskScheduler noOpTaskScheduler() {
            return new NoOpTaskScheduler();
        }

        private static final class NoOpTaskScheduler implements TaskScheduler {

            private static final ScheduledFuture<Object> NOOP = new ScheduledFuture<>() {
                @Override public long getDelay(TimeUnit unit) { return 0L; }
                @Override public int compareTo(java.util.concurrent.Delayed other) { return 0; }
                @Override public boolean cancel(boolean mayInterruptIfRunning) { return true; }
                @Override public boolean isCancelled() { return true; }
                @Override public boolean isDone() { return true; }
                @Override public Object get() { return null; }
                @Override public Object get(long timeout, TimeUnit unit) { return null; }
            };

            @Override public Clock getClock() { return Clock.systemDefaultZone(); }
            @Override public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) { return NOOP; }
            @Override public ScheduledFuture<?> schedule(Runnable task, Instant startTime) { return NOOP; }
            @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) { return NOOP; }
            @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) { return NOOP; }
            @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) { return NOOP; }
            @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) { return NOOP; }
        }
    }
}
