package com.chunbaetour.domain.admin.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.admin.place.dto.request.AdminPlaceUpdateRequest;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AdminPlaceService} 단위 테스트 (Admin S07).
 *
 * <p>repository를 mock해 partial update(null-skip) 위임, soft delete 전이, 미존재 404, S10 대시보드 의존
 * getTotalPlaces 카운트를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminPlaceServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    private AdminPlaceService service() {
        return new AdminPlaceService(placeRepository);
    }

    private Place activePlace() {
        return Place.builder()
                .name("경복궁")
                .category(PlaceCategory.TOURIST_SPOT)
                .description("조선 왕궁")
                .address("서울 종로구")
                .lat(new BigDecimal("37.5796"))
                .lng(new BigDecimal("126.9770"))
                .operatingHours("09:00-18:00")
                .phone("02-000-0000")
                .build();
    }

    @Test
    @DisplayName("수정: null 필드는 무시하고 지정 필드만 반영 (partial update)")
    void updatePlace_partial_skipsNull() {
        Place place = activePlace();
        given(placeRepository.findById(1L)).willReturn(Optional.of(place));

        // name/phone만 변경, 나머지 null
        AdminPlaceUpdateRequest request = new AdminPlaceUpdateRequest(
                "경복궁(수정)", null, null, null, null, "02-111-1111", null, null);

        var result = service().updatePlace(1L, request);

        assertThat(result.name()).isEqualTo("경복궁(수정)");
        assertThat(result.phone()).isEqualTo("02-111-1111");
        // 미지정 필드 보존
        assertThat(result.description()).isEqualTo("조선 왕궁");
        assertThat(result.address()).isEqualTo("서울 종로구");
        assertThat(result.operatingHours()).isEqualTo("09:00-18:00");
    }

    @Test
    @DisplayName("수정: 미존재 placeId → PLACE_NOT_FOUND(404)")
    void updatePlace_notFound() {
        given(placeRepository.findById(999L)).willReturn(Optional.empty());
        AdminPlaceUpdateRequest request = new AdminPlaceUpdateRequest(
                "x", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service().updatePlace(999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제: Place.delete() → status DELETED 전이 (soft delete)")
    void deletePlace_softDelete() {
        Place place = activePlace();
        given(placeRepository.findById(1L)).willReturn(Optional.of(place));

        service().deletePlace(1L);

        assertThat(place.getStatus()).isEqualTo(PlaceStatus.DELETED);
        // hard delete 미구현 — repository.delete 호출 없음
        then(placeRepository).should(never()).delete(any());
        then(placeRepository).should(never()).deleteById(any());
    }

    @Test
    @DisplayName("삭제: 미존재 placeId → PLACE_NOT_FOUND(404)")
    void deletePlace_notFound() {
        given(placeRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().deletePlace(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("getTotalPlaces: repository.count() 위임")
    void getTotalPlaces_delegatesCount() {
        given(placeRepository.count()).willReturn(42L);

        assertThat(service().getTotalPlaces()).isEqualTo(42L);
    }
}
