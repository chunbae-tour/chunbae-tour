package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.client.TourApiPlaceItem;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.service.PlaceSyncBatchService.ChunkResult;
import com.chunbaetour.domain.place.service.PlaceSyncBatchService.UpsertResult;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceSource;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class PlaceSyncBatchServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlaceSyncBatchService batchService;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void cleanup() {
        placeRepository.deleteAll();
    }

    /** 상세 캐시 키를 미리 채운다 — sync UPDATE/DELETE 후 무효화 검증용(B10). */
    private void seedDetailCache(Long placeId) {
        stringRedisTemplate.opsForValue().set(
                PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + placeId, "{\"cached\":true}",
                Duration.ofMinutes(PlaceRedisConstants.PLACE_DETAIL_CACHE_TTL_MINUTES));
    }

    private boolean detailCacheExists(Long placeId) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + placeId));
    }

    private TourApiPlaceItem item(String contentId, String title, String mapX, String mapY) {
        return new TourApiPlaceItem(contentId, title, "충청남도 천안시 동남구", "", mapX, mapY,
                "http://tong.visitkorea.or.kr/img.jpg", null, "041-100-1000", "20251124134437", "1", "34");
    }

    private TourApiPlaceItem itemWithModified(String contentId, String modifiedTime) {
        return new TourApiPlaceItem(contentId, "관광지", "충청남도 천안시 동남구", "", "127.1", "36.8",
                null, null, null, modifiedTime, "1", "34");
    }

    @Test
    @DisplayName("신규 contentid는 CREATED로 저장되며 source=API_FETCH·category=TOURIST_SPOT가 설정된다")
    void create() {
        UpsertResult result = batchService.upsertItem(item("100", "독립기념관", "127.215", "36.785"));

        assertThat(result).isEqualTo(UpsertResult.CREATED);
        Place saved = placeRepository.findByExternalId("100").orElseThrow();
        assertThat(saved.getName()).isEqualTo("독립기념관");
        assertThat(saved.getSource()).isEqualTo(PlaceSource.API_FETCH);
        assertThat(saved.getCategory()).isEqualTo(PlaceCategory.TOURIST_SPOT);
        assertThat(saved.getThumbnailUrl()).isEqualTo("http://tong.visitkorea.or.kr/img.jpg");
        assertThat(saved.getLat()).isNotNull();
        assertThat(saved.getLng()).isNotNull();
    }

    @Test
    @DisplayName("동일 contentid 재수집은 UPDATED이며 이름·좌표가 갱신된다")
    void update() {
        batchService.upsertItem(item("200", "옛이름", "127.1", "36.8"));

        UpsertResult result = batchService.upsertItem(item("200", "새이름", "127.2", "36.9"));

        assertThat(result).isEqualTo(UpsertResult.UPDATED);
        Place updated = placeRepository.findByExternalId("200").orElseThrow();
        assertThat(updated.getName()).isEqualTo("새이름");
        assertThat(placeRepository.findAll()).hasSize(1); // 중복 생성 없이 1건 유지
    }

    @Test
    @DisplayName("재동기화 시 외부 modifiedtime이 증가하면 enrichAttemptCount가 리셋되어 상세 재수집 대상이 된다")
    void resyncNewerModifiedTimeResetsEnrichRetries() {
        batchService.upsertItem(itemWithModified("800", "20260101000000"));
        // 한도 소진 시뮬레이션
        Place place = placeRepository.findByExternalId("800").orElseThrow();
        for (int i = 0; i < Place.MAX_ENRICH_ATTEMPTS; i++) {
            place.recordEmptyEnrichAttempt();
        }
        placeRepository.saveAndFlush(place);
        assertThat(placeRepository.findByExternalId("800").orElseThrow().needsDetailEnrichment()).isFalse();

        // 외부 데이터 갱신(modifiedtime 증가) 재동기화
        batchService.upsertItem(itemWithModified("800", "20260601000000"));

        Place after = placeRepository.findByExternalId("800").orElseThrow();
        assertThat(after.getEnrichAttemptCount()).isZero();
        assertThat(after.needsDetailEnrichment()).isTrue();
    }

    @Test
    @DisplayName("재동기화 시 외부 modifiedtime이 동일하면 enrichAttemptCount를 리셋하지 않는다(API 반복 호출 방지)")
    void resyncSameModifiedTimeKeepsEnrichRetries() {
        batchService.upsertItem(itemWithModified("810", "20260101000000"));
        Place place = placeRepository.findByExternalId("810").orElseThrow();
        for (int i = 0; i < Place.MAX_ENRICH_ATTEMPTS; i++) {
            place.recordEmptyEnrichAttempt();
        }
        placeRepository.saveAndFlush(place);

        // 동일 modifiedtime(==boundary 재수집 상황) 재동기화 — 리셋되면 안 됨
        batchService.upsertItem(itemWithModified("810", "20260101000000"));

        Place after = placeRepository.findByExternalId("810").orElseThrow();
        assertThat(after.getEnrichAttemptCount()).isEqualTo(Place.MAX_ENRICH_ATTEMPTS);
        assertThat(after.needsDetailEnrichment()).isFalse();
    }

    @Test
    @DisplayName("@Version — upsertItem UPDATE 시 낙관락 version이 1 증가한다 (KAN-304/B12)")
    void versionIncrementsOnUpdate() {
        batchService.upsertItem(item("910", "옛이름", "127.1", "36.8"));
        Long v0 = placeRepository.findByExternalId("910").orElseThrow().getVersion();

        batchService.upsertItem(item("910", "새이름", "127.2", "36.9"));
        Long v1 = placeRepository.findByExternalId("910").orElseThrow().getVersion();

        // version 컬럼이 매 UPDATE마다 증가 → 동시 UPDATE 시 한쪽이 OptimisticLockingFailureException으로 차단됨
        assertThat(v0).isZero();
        assertThat(v1).isEqualTo(v0 + 1);
    }

    @Test
    @DisplayName("좌표가 없으면 SKIPPED 처리되고 저장되지 않는다")
    void skipMissingCoords() {
        UpsertResult result = batchService.upsertItem(item("300", "좌표없음", "", ""));

        assertThat(result).isEqualTo(UpsertResult.SKIPPED);
        assertThat(placeRepository.findByExternalId("300")).isEmpty();
    }

    @Test
    @DisplayName("운영자가 삭제(DELETED)한 관광지는 재수집해도 SKIPPED로 되살리지 않는다")
    void preserveDeleted() {
        // createFromApi(externalId, name, address, lat, lng, thumbnail, phone, sido, sigungu)
        Place toDelete = Place.createFromApi("400", "삭제된관광지", "충청남도 천안시",
                java.math.BigDecimal.valueOf(36.8), java.math.BigDecimal.valueOf(127.1), null, null, "충청남도", "천안시");
        toDelete.delete();
        placeRepository.saveAndFlush(toDelete);

        UpsertResult result = batchService.upsertItem(item("400", "되살리기시도", "127.2", "36.9"));

        assertThat(result).isEqualTo(UpsertResult.SKIPPED);
        Optional<Place> found = placeRepository.findByExternalId("400");
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(PlaceStatus.DELETED);
        assertThat(found.get().getName()).isEqualTo("삭제된관광지"); // 갱신 안 됨
    }

    @Test
    @DisplayName("showflag 삭제 항목(markDeleted)은 기존 관광지를 soft-delete(DELETED)한다")
    void markDeletedSoftDeletes() {
        batchService.upsertItem(item("500", "삭제예정관광지", "127.1", "36.8"));

        UpsertResult result = batchService.markDeleted("500");

        assertThat(result).isEqualTo(UpsertResult.DELETED);
        assertThat(placeRepository.findByExternalId("500").orElseThrow().getStatus())
                .isEqualTo(PlaceStatus.DELETED);
    }

    @Test
    @DisplayName("존재하지 않는 contentId의 삭제 요청은 SKIPPED")
    void markDeletedNotFound() {
        assertThat(batchService.markDeleted("999")).isEqualTo(UpsertResult.SKIPPED);
    }

    @Test
    @DisplayName("UPDATE 롤백 시 afterCommit 미발화 → 상세 캐시가 evict되지 않는다 (B10 일관성, KAN-303 리뷰)")
    void rollbackDoesNotEvictDetailCache() {
        batchService.upsertItem(item("730", "옛이름", "127.1", "36.8"));
        Long placeId = placeRepository.findByExternalId("730").orElseThrow().getId();
        seedDetailCache(placeId);
        assertThat(detailCacheExists(placeId)).isTrue();

        // 같은 contentId 재수집(UPDATE)인데 name이 컬럼 한도(varchar 100) 초과 → flush 시 제약 위반으로 롤백.
        // evict는 saveAndFlush 성공 이후(afterCommit)에만 등록되므로, 롤백 경로에선 캐시가 그대로 남아야 한다.
        String tooLongName = "가".repeat(150);
        assertThatThrownBy(() -> batchService.upsertItem(item("730", tooLongName, "127.2", "36.9")))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 롤백 → afterCommit 미발화 → 캐시 키 유지(stale은 자연 TTL로 치유)
        assertThat(detailCacheExists(placeId)).isTrue();
    }

    @Test
    @DisplayName("upsertChunk 갱신분이 없으면(전부 SKIP) evict를 등록하지 않는다 — 캐시 그대로 (updatedIds.isEmpty 가드)")
    void upsertChunkNoUpdates_doesNotEvict() {
        // HIDDEN 관광지는 upsertChunk가 SKIP(updatedIds 미포함) → evict 미등록
        batchService.upsertItem(item("740", "숨김관광지", "127.1", "36.8"));
        Place hidden = placeRepository.findByExternalId("740").orElseThrow();
        hidden.hide();
        placeRepository.saveAndFlush(hidden);
        Long placeId = hidden.getId();
        seedDetailCache(placeId);

        batchService.upsertChunk(List.of(item("740", "갱신시도", "127.2", "36.9")));

        // 갱신분 0건 → evict 미등록 → 캐시 유지
        assertThat(detailCacheExists(placeId)).isTrue();
    }

    @Test
    @DisplayName("markDeleted(DELETED)는 커밋 후 상세 캐시(place:{id})를 무효화한다 (B10)")
    void markDeletedEvictsDetailCache() {
        batchService.upsertItem(item("700", "삭제예정관광지", "127.1", "36.8"));
        Long placeId = placeRepository.findByExternalId("700").orElseThrow().getId();
        seedDetailCache(placeId);
        assertThat(detailCacheExists(placeId)).isTrue();

        batchService.markDeleted("700");

        // REQUIRES_NEW 커밋 후 afterCommit에서 캐시 키 삭제
        assertThat(detailCacheExists(placeId)).isFalse();
    }

    @Test
    @DisplayName("upsertItem(UPDATED)은 커밋 후 상세 캐시(place:{id})를 무효화한다 (B10)")
    void upsertUpdateEvictsDetailCache() {
        batchService.upsertItem(item("710", "옛이름", "127.1", "36.8"));
        Long placeId = placeRepository.findByExternalId("710").orElseThrow().getId();
        seedDetailCache(placeId);
        assertThat(detailCacheExists(placeId)).isTrue();

        batchService.upsertItem(item("710", "새이름", "127.2", "36.9"));

        assertThat(detailCacheExists(placeId)).isFalse();
    }

    @Test
    @DisplayName("upsertChunk 갱신분은 커밋 후 상세 캐시를 다중 키 DEL로 모두 무효화한다 (B10, KAN-303 리뷰)")
    void upsertChunkUpdateEvictsDetailCache() {
        batchService.upsertChunk(List.of(
                item("720", "옛이름", "127.1", "36.8"),
                item("721", "다른관광지", "127.2", "36.9")));
        Long placeId720 = placeRepository.findByExternalId("720").orElseThrow().getId();
        Long placeId721 = placeRepository.findByExternalId("721").orElseThrow().getId();
        // 두 갱신분 모두 캐시 seed — 일부 키만 삭제하는 퇴행을 잡기 위해 둘 다 검증
        seedDetailCache(placeId720);
        seedDetailCache(placeId721);
        assertThat(detailCacheExists(placeId720)).isTrue();
        assertThat(detailCacheExists(placeId721)).isTrue();

        batchService.upsertChunk(List.of(
                item("720", "새이름", "127.3", "37.0"),
                item("721", "또다른이름", "127.4", "37.1")));

        assertThat(detailCacheExists(placeId720)).isFalse();
        assertThat(detailCacheExists(placeId721)).isFalse();
    }

    @Test
    @DisplayName("운영자가 숨김(HIDDEN)한 관광지는 showflag 삭제(markDeleted)가 와도 건드리지 않고 SKIPPED")
    void markDeletedSkipsHidden() {
        batchService.upsertItem(item("600", "숨김관광지", "127.1", "36.8"));
        Place hidden = placeRepository.findByExternalId("600").orElseThrow();
        hidden.hide();
        placeRepository.saveAndFlush(hidden);

        UpsertResult result = batchService.markDeleted("600");

        // 운영자 숨김 의사 존중 — sync 삭제가 DELETED로 덮어쓰지 않는다
        assertThat(result).isEqualTo(UpsertResult.SKIPPED);
        assertThat(placeRepository.findByExternalId("600").orElseThrow().getStatus())
                .isEqualTo(PlaceStatus.HIDDEN);
    }

    @Test
    @DisplayName("운영자가 숨김(HIDDEN)한 관광지는 재수집(upsert)해도 SKIPPED로 데이터를 덮어쓰지 않는다")
    void preserveHidden() {
        batchService.upsertItem(item("700", "숨김관광지", "127.1", "36.8"));
        Place hidden = placeRepository.findByExternalId("700").orElseThrow();
        hidden.hide();
        placeRepository.saveAndFlush(hidden);

        UpsertResult result = batchService.upsertItem(item("700", "덮어쓰기시도", "127.2", "36.9"));

        // ACTIVE만 갱신 — HIDDEN은 이름/좌표 갱신 없이 보존
        assertThat(result).isEqualTo(UpsertResult.SKIPPED);
        Place found = placeRepository.findByExternalId("700").orElseThrow();
        assertThat(found.getStatus()).isEqualTo(PlaceStatus.HIDDEN);
        assertThat(found.getName()).isEqualTo("숨김관광지"); // 갱신 안 됨
    }

    @Test
    @DisplayName("upsertChunk는 신규 생성과 기존 갱신을 한 번의 청크로 처리하고 집계를 반환한다")
    void upsertChunkCreatesAndUpdates() {
        // 기존 1건(910) 선저장 → 청크에서 갱신 대상
        batchService.upsertItem(item("910", "옛이름", "127.1", "36.8"));

        ChunkResult result = batchService.upsertChunk(List.of(
                item("900", "신규관광지", "127.215", "36.785"),  // CREATE
                item("910", "새이름", "127.2", "36.9")));        // UPDATE

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(placeRepository.findByExternalId("900")).isPresent();
        assertThat(placeRepository.findByExternalId("910").orElseThrow().getName()).isEqualTo("새이름");
    }

    @Test
    @DisplayName("upsertChunk는 좌표 없는 무효 item만 skip하고 정상 item은 저장한다")
    void upsertChunkSkipsInvalidKeepsValid() {
        ChunkResult result = batchService.upsertChunk(List.of(
                item("920", "정상", "127.1", "36.8"),
                item("921", "좌표없음", "", "")));   // 무효 → skip

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(placeRepository.findByExternalId("920")).isPresent();
        assertThat(placeRepository.findByExternalId("921")).isEmpty();
    }

    @Test
    @DisplayName("upsertChunk는 운영자 숨김(HIDDEN) 기존 관광지를 skip으로 보존한다")
    void upsertChunkPreservesNonActive() {
        batchService.upsertItem(item("930", "숨김관광지", "127.1", "36.8"));
        Place hidden = placeRepository.findByExternalId("930").orElseThrow();
        hidden.hide();
        placeRepository.saveAndFlush(hidden);

        ChunkResult result = batchService.upsertChunk(List.of(item("930", "덮어쓰기시도", "127.2", "36.9")));

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        Place found = placeRepository.findByExternalId("930").orElseThrow();
        assertThat(found.getStatus()).isEqualTo(PlaceStatus.HIDDEN);
        assertThat(found.getName()).isEqualTo("숨김관광지");
    }
}
