package com.chunbaetour.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.festival.client.TourApiClient;
import com.chunbaetour.domain.festival.client.TourApiFestivalItem;
import com.chunbaetour.domain.festival.dto.response.FestivalFetchResult;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FestivalFetchServiceTest {

    @Mock TourApiClient tourApiClient;
    @Mock FestivalRepository festivalRepository;
    @Mock FestivalCacheEvictUtil cacheEvict;
    @InjectMocks FestivalFetchService fetchService;

    @BeforeEach
    void setUp() {
        // self 필드는 @Autowired @Lazy 자기 프록시 — Spring 없이 주입 필요
        ReflectionTestUtils.setField(fetchService, "self", fetchService);
    }

    private TourApiFestivalItem item(String insttCode) {
        return new TourApiFestivalItem(insttCode, "의령 리치리치 페스티벌", "서동생활공원",
                "2026-10-02", "2026-10-05", "개막식+메인프로그램",
                "의령 리치리치 페스티벌 추진위원회",
                "경상남도 의령군 의령읍 의병로8길 44",
                "https://www.uiryeong.go.kr/festival", "055-570-2512",
                "35.31545351", "128.2558931", "경상남도 의령군");
    }

    private String externalId(String insttCode) {
        return insttCode + "_의령 리치리치 페스티벌";
    }

    @Test
    void 신규항목_저장_created_1() {
        given(tourApiClient.fetchAll()).willReturn(List.of(item("5390000")));
        given(festivalRepository.findByExternalId(externalId("5390000"))).willReturn(Optional.empty());
        given(festivalRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        FestivalFetchResult result = fetchService.fetchNow();

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(0);
        verify(festivalRepository).save(any(Festival.class));
    }

    @Test
    void 기존항목_ACTIVE_updateFromApi_호출() {
        Festival existing = Festival.createFromApi(
                externalId("5390000"), "Old Name", "경상남도", "경상남도 의령군 의령읍",
                LocalDate.of(2025, 10, 2), LocalDate.of(2025, 10, 5), null);
        given(tourApiClient.fetchAll()).willReturn(List.of(item("5390000")));
        given(festivalRepository.findByExternalId(externalId("5390000"))).willReturn(Optional.of(existing));

        FestivalFetchResult result = fetchService.fetchNow();

        assertThat(result.created()).isEqualTo(0);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(0);
        assertThat(existing.getName()).isEqualTo("의령 리치리치 페스티벌");
        verify(festivalRepository, never()).save(any());
        verify(cacheEvict).evictAll();
    }

    @Test
    void 기존항목_DELETED_updateFromApi_미호출() {
        Festival deleted = Festival.createFromApi(
                externalId("5390000"), "Old Name", "경상남도", "경상남도 의령군 의령읍",
                LocalDate.of(2025, 10, 2), LocalDate.of(2025, 10, 5), null);
        deleted.delete();
        given(tourApiClient.fetchAll()).willReturn(List.of(item("5390000")));
        given(festivalRepository.findByExternalId(externalId("5390000"))).willReturn(Optional.of(deleted));

        FestivalFetchResult result = fetchService.fetchNow();

        assertThat(deleted.getName()).isEqualTo("Old Name");
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(0);
    }

    @Test
    void insttCode_없는항목_스킵() {
        TourApiFestivalItem invalid = new TourApiFestivalItem(null, "축제", "서동생활공원",
                "2026-10-02", "2026-10-05", "내용", "주관", "경상남도 의령군",
                "", "", "35.0", "128.0", "경상남도 의령군");
        given(tourApiClient.fetchAll()).willReturn(List.of(invalid));

        FestivalFetchResult result = fetchService.fetchNow();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(0);
        verify(festivalRepository, never()).findByExternalId(any());
    }

    @Test
    void 날짜역전_항목_스킵() {
        TourApiFestivalItem invalid = new TourApiFestivalItem("5390000", "축제", "장소",
                "2026-10-05", "2026-10-02", "내용", "주관",
                "경상남도 의령군 의령읍", "", "", "", "", "경상남도 의령군");
        given(tourApiClient.fetchAll()).willReturn(List.of(invalid));

        FestivalFetchResult result = fetchService.fetchNow();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(0);
        verify(festivalRepository, never()).findByExternalId(any());
    }

    @Test
    void upsertItem_예외_스킵후_다음항목_계속_처리() {
        TourApiFestivalItem bad  = item("5390000");
        TourApiFestivalItem good = item("3050000");
        given(tourApiClient.fetchAll()).willReturn(List.of(bad, good));
        given(festivalRepository.findByExternalId(externalId("5390000"))).willThrow(new RuntimeException("DB error"));
        given(festivalRepository.findByExternalId(externalId("3050000"))).willReturn(Optional.empty());
        given(festivalRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        FestivalFetchResult result = fetchService.fetchNow();

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void created_0이면_cacheEvict_미호출() {
        given(tourApiClient.fetchAll()).willReturn(List.of());

        fetchService.fetchNow();

        verify(cacheEvict, never()).evictAll();
    }

    @Test
    void created_있으면_cacheEvict_호출() {
        given(tourApiClient.fetchAll()).willReturn(List.of(item("5390000")));
        given(festivalRepository.findByExternalId(externalId("5390000"))).willReturn(Optional.empty());
        given(festivalRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        fetchService.fetchNow();

        verify(cacheEvict).evictAll();
    }

    @Test
    void externalId_insttCode_fstvlNm_조합() {
        given(tourApiClient.fetchAll()).willReturn(List.of(item("5390000")));
        given(festivalRepository.findByExternalId(externalId("5390000"))).willReturn(Optional.empty());
        ArgumentCaptor<Festival> captor = ArgumentCaptor.forClass(Festival.class);
        given(festivalRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

        fetchService.fetchNow();

        assertThat(captor.getValue().getExternalId()).isEqualTo("5390000_의령 리치리치 페스티벌");
    }

    @Test
    void region_rdnmadr_첫번째_토큰() {
        given(tourApiClient.fetchAll()).willReturn(List.of(item("5390000")));
        given(festivalRepository.findByExternalId(externalId("5390000"))).willReturn(Optional.empty());
        ArgumentCaptor<Festival> captor = ArgumentCaptor.forClass(Festival.class);
        given(festivalRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

        fetchService.fetchNow();

        assertThat(captor.getValue().getRegion()).isEqualTo("경상남도");
    }
}
