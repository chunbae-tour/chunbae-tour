package com.chunbaetour.domain.admin.banner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.admin.banner.dto.request.AdminBannerCreateRequest;
import com.chunbaetour.domain.admin.banner.dto.request.AdminBannerUpdateRequest;
import com.chunbaetour.domain.banner.Banner;
import com.chunbaetour.domain.banner.repository.BannerRepository;
import com.chunbaetour.domain.banner.type.BannerStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AdminBannerService} 단위 테스트 (Admin S09, KAN-216).
 *
 * <p>repository를 mock해 partial update(null-skip) 위임, soft delete 전이, 미존재 404, 이미 삭제 409,
 * getTotalBanners 카운트, 목록 sentinel(size+1) hasNext/nextCursor를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminBannerServiceTest {

    @Mock
    private BannerRepository bannerRepository;

    private AdminBannerService service() {
        return new AdminBannerService(bannerRepository);
    }

    private Banner activeBanner() {
        return Banner.builder()
                .title("여름 축제")
                .imageUrl("https://cdn/summer.png")
                .linkUrl("https://event/summer")
                .priority(5)
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 8, 31))
                .build();
    }

    @Test
    @DisplayName("수정: null 필드는 무시하고 지정 필드만 반영 (partial update)")
    void updateBanner_partial_skipsNull() {
        Banner banner = activeBanner();
        given(bannerRepository.findById(1L)).willReturn(Optional.of(banner));

        // title/priority만 변경, 나머지 null
        AdminBannerUpdateRequest request = new AdminBannerUpdateRequest(
                "여름 축제(수정)", null, null, 1, null, null);

        var result = service().updateBanner(1L, request);

        assertThat(result.title()).isEqualTo("여름 축제(수정)");
        assertThat(result.priority()).isEqualTo(1);
        // 미지정 필드 보존
        assertThat(result.imageUrl()).isEqualTo("https://cdn/summer.png");
        assertThat(result.linkUrl()).isEqualTo("https://event/summer");
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("수정: 한쪽 날짜만 수정해 기존 값과 역전되면 IllegalArgumentException (병합 재검증)")
    void updateBanner_periodInversion_rejected() {
        Banner banner = activeBanner(); // start=7/1, end=8/31
        given(bannerRepository.findById(1L)).willReturn(Optional.of(banner));

        // endDate만 6/1로 수정 → 기존 startDate(7/1)보다 빨라 역전
        AdminBannerUpdateRequest request = new AdminBannerUpdateRequest(
                null, null, null, null, null, LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> service().updateBanner(1L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("수정: 미존재 bannerId → BANNER_NOT_FOUND(404)")
    void updateBanner_notFound() {
        given(bannerRepository.findById(999L)).willReturn(Optional.empty());
        AdminBannerUpdateRequest request = new AdminBannerUpdateRequest(
                "x", null, null, null, null, null);

        assertThatThrownBy(() -> service().updateBanner(999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BANNER_NOT_FOUND);
    }

    @Test
    @DisplayName("수정: 이미 DELETED → BANNER_ALREADY_DELETED(409) (deleteBanner와 동일 가드)")
    void updateBanner_alreadyDeleted() {
        Banner banner = activeBanner();
        banner.delete();
        given(bannerRepository.findById(1L)).willReturn(Optional.of(banner));
        AdminBannerUpdateRequest request = new AdminBannerUpdateRequest(
                "여름 축제(수정)", null, null, null, null, null);

        assertThatThrownBy(() -> service().updateBanner(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BANNER_ALREADY_DELETED);
    }

    @Test
    @DisplayName("삭제: Banner.delete() → status DELETED 전이 (soft delete)")
    void deleteBanner_softDelete() {
        Banner banner = activeBanner();
        given(bannerRepository.findById(1L)).willReturn(Optional.of(banner));

        service().deleteBanner(1L);

        assertThat(banner.getStatus()).isEqualTo(BannerStatus.DELETED);
        then(bannerRepository).should(never()).delete(any());
        then(bannerRepository).should(never()).deleteById(any());
    }

    @Test
    @DisplayName("삭제: 미존재 bannerId → BANNER_NOT_FOUND(404)")
    void deleteBanner_notFound() {
        given(bannerRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().deleteBanner(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BANNER_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제: 이미 DELETED → BANNER_ALREADY_DELETED(409) (멱등 가드)")
    void deleteBanner_alreadyDeleted() {
        Banner banner = activeBanner();
        banner.delete();
        given(bannerRepository.findById(1L)).willReturn(Optional.of(banner));

        assertThatThrownBy(() -> service().deleteBanner(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BANNER_ALREADY_DELETED);
        then(bannerRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("등록: toEntity 저장 후 상세 반환")
    void createBanner_savesAndReturnsDetail() {
        Banner saved = activeBanner();
        given(bannerRepository.save(any(Banner.class))).willReturn(saved);
        AdminBannerCreateRequest request = new AdminBannerCreateRequest(
                "여름 축제", "https://cdn/summer.png", "https://event/summer",
                5, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31));

        var result = service().createBanner(request);

        assertThat(result.title()).isEqualTo("여름 축제");
        then(bannerRepository).should().save(any(Banner.class));
    }

    @Test
    @DisplayName("목록: 첫 페이지(cursor null)는 searchForAdmin(status, null, null) 위임 + sentinel로 hasNext 판단")
    void getBanners_firstPage_sentinelHasNext() {
        // size=1 요청에 2건(size+1) 반환 → hasNext=true, content 1건, nextCursor 발급
        given(bannerRepository.searchForAdmin(isNull(), isNull(), isNull(), any()))
                .willReturn(List.of(activeBanner(), activeBanner()));

        var result = service().getBanners(null, null, 1);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.nextCursor()).isNotNull();
        then(bannerRepository).should().searchForAdmin(isNull(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("목록: 잘못된 cursor → INVALID_CURSOR(400)")
    void getBanners_invalidCursor() {
        assertThatThrownBy(() -> service().getBanners(null, "!!!not-base64!!!", 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    @Test
    @DisplayName("getTotalBanners: countByStatusNot(DELETED) 위임 (soft delete 제외)")
    void getTotalBanners_excludesDeleted() {
        given(bannerRepository.countByStatusNot(BannerStatus.DELETED)).willReturn(7L);

        assertThat(service().getTotalBanners()).isEqualTo(7L);
    }
}
