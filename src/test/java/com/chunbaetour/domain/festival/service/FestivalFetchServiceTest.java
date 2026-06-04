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

    private TourApiFestivalItem item(String contentid) {
        return new TourApiFestivalItem(contentid, "Seoul Festival", "Seoul Korea",
                "20260701", "20260710", "https://img.example.com/1.jpg", "11");
    }

    @Test
    void 신규항목_저장_created_1() {
        given(tourApiClient.fetchAll()).willReturn(List.of(item("C001")));
        given(festivalRepository.findByExternalId("C001")).willReturn(Optional.empty());
        given(festivalRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        FestivalFetchResult result = fetchService.fetchNow();

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(0);
        verify(festivalRepository).save(any(Festival.class));
    }

    @Test
    void 기존항목_ACTIVE_updateFromApi_호출() {
        Festival existing = Festival.createFromApi("C001", "Old Name", "서울특별시", "Seoul Korea",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), null);
        given(tourApiClient.fetchAll()).willReturn(List.of(item("C001")));
        given(festivalRepository.findByExternalId("C001")).willReturn(Optional.of(existing));

        FestivalFetchResult result = fetchService.fetchNow();

        assertThat(result.created()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(existing.getName()).isEqualTo("Seoul Festival");
        verify(festivalRepository, never()).save(any());
    }

    @Test
    void 기존항목_DELETED_updateFromApi_미호출() {
        Festival deleted = Festival.createFromApi("C001", "Old Name", "서울특별시", "Seoul Korea",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), null);
        deleted.delete();
        given(tourApiClient.fetchAll()).willReturn(List.of(item("C001")));
        given(festivalRepository.findByExternalId("C001")).willReturn(Optional.of(deleted));

        FestivalFetchResult result = fetchService.fetchNow();

        assertThat(deleted.getName()).isEqualTo("Old Name");
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(0);
    }

    @Test
    void contentid_없는항목_스킵() {
        TourApiFestivalItem invalid = new TourApiFestivalItem(null, "Festival", "Seoul",
                "20260701", "20260710", null, "11");
        given(tourApiClient.fetchAll()).willReturn(List.of(invalid));

        FestivalFetchResult result = fetchService.fetchNow();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(0);
        verify(festivalRepository, never()).findByExternalId(any());
    }

    @Test
    void 날짜역전_항목_스킵() {
        TourApiFestivalItem invalid = new TourApiFestivalItem("C001", "Festival", "Seoul",
                "20260710", "20260701", null, "11");
        given(tourApiClient.fetchAll()).willReturn(List.of(invalid));

        FestivalFetchResult result = fetchService.fetchNow();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(0);
        verify(festivalRepository, never()).findByExternalId(any());
    }

    @Test
    void upsertItem_예외_스킵후_다음항목_계속_처리() {
        given(tourApiClient.fetchAll()).willReturn(List.of(item("C001"), item("C002")));
        given(festivalRepository.findByExternalId("C001")).willThrow(new RuntimeException("DB error"));
        given(festivalRepository.findByExternalId("C002")).willReturn(Optional.empty());
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
        given(tourApiClient.fetchAll()).willReturn(List.of(item("C001")));
        given(festivalRepository.findByExternalId("C001")).willReturn(Optional.empty());
        given(festivalRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        fetchService.fetchNow();

        verify(cacheEvict).evictAll();
    }

    @Test
    void lDongRegnCd_서울코드_서울특별시_매핑() {
        given(tourApiClient.fetchAll()).willReturn(List.of(item("C001")));
        given(festivalRepository.findByExternalId("C001")).willReturn(Optional.empty());
        ArgumentCaptor<Festival> captor = ArgumentCaptor.forClass(Festival.class);
        given(festivalRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

        fetchService.fetchNow();

        assertThat(captor.getValue().getRegion()).isEqualTo("서울특별시");
    }

    @Test
    void 알수없는코드_addr1_공백분리_첫번째_토큰() {
        // resolveRegion: lDongRegnCd unknown → addr1.split(" ")[0]
        TourApiFestivalItem unknown = new TourApiFestivalItem("C001", "Festival", "Busan Metro City",
                "20260701", "20260710", null, "99");
        given(tourApiClient.fetchAll()).willReturn(List.of(unknown));
        given(festivalRepository.findByExternalId("C001")).willReturn(Optional.empty());
        ArgumentCaptor<Festival> captor = ArgumentCaptor.forClass(Festival.class);
        given(festivalRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

        fetchService.fetchNow();

        assertThat(captor.getValue().getRegion()).isEqualTo("Busan");
    }
}
