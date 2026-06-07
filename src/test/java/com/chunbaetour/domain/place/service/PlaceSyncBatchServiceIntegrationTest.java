package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.client.TourApiPlaceItem;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.service.PlaceSyncBatchService.UpsertResult;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceSource;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PlaceSyncBatchServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlaceSyncBatchService batchService;

    @Autowired
    private PlaceRepository placeRepository;

    @AfterEach
    void cleanup() {
        placeRepository.deleteAll();
    }

    private TourApiPlaceItem item(String contentId, String title, String mapX, String mapY) {
        return new TourApiPlaceItem(contentId, title, "충청남도 천안시 동남구", "", mapX, mapY,
                "http://tong.visitkorea.or.kr/img.jpg", null, "041-100-1000", "20251124134437", "1", "34");
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
}
