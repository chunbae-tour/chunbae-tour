package com.chunbaetour.domain.admin.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.admin.place.dto.request.AdminPlaceCreateRequest;
import com.chunbaetour.domain.admin.place.dto.request.AdminPlaceUpdateRequest;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import java.math.BigDecimal;
import java.util.List;
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
    @DisplayName("수정: 이미 DELETED → PLACE_ALREADY_DELETED(409) (M1, deletePlace와 동일 가드)")
    void updatePlace_alreadyDeleted() {
        Place place = activePlace();
        place.delete();
        given(placeRepository.findById(1L)).willReturn(Optional.of(place));
        AdminPlaceUpdateRequest request = new AdminPlaceUpdateRequest(
                "경복궁(수정)", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service().updatePlace(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLACE_ALREADY_DELETED);
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
    @DisplayName("삭제: 이미 DELETED → PLACE_ALREADY_DELETED(409) (멱등 가드)")
    void deletePlace_alreadyDeleted() {
        Place place = activePlace();
        place.delete(); // 이미 DELETED 상태
        given(placeRepository.findById(1L)).willReturn(Optional.of(place));

        assertThatThrownBy(() -> service().deletePlace(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLACE_ALREADY_DELETED);
        then(placeRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("등록: toEntity 저장 후 상세 반환")
    void createPlace_savesAndReturnsDetail() {
        Place saved = activePlace();
        given(placeRepository.save(any(Place.class))).willReturn(saved);
        AdminPlaceCreateRequest request = new AdminPlaceCreateRequest(
                "경복궁", PlaceCategory.TOURIST_SPOT, "조선 왕궁", "서울 종로구",
                new BigDecimal("37.5796"), new BigDecimal("126.9770"),
                null, null, null, null, null, null, null);

        var result = service().createPlace(request);

        assertThat(result.name()).isEqualTo("경복궁");
        then(placeRepository).should().save(any(Place.class));
    }

    @Test
    @DisplayName("목록: keyword의 LIKE 와일드카드(% _ \\)를 이스케이프해 repository에 전달")
    void getPlaces_escapesLikeWildcards() {
        // searchForAdmin에 리터럴화된 keyword가 넘어가는지 검증(F). 결과는 빈 리스트 → hasNext=false.
        given(placeRepository.searchForAdmin(eq("50\\%\\_x"), isNull(), isNull(), any()))
                .willReturn(List.of());

        var result = service().getPlaces("50%_x", null, null, 20);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        then(placeRepository).should().searchForAdmin(eq("50\\%\\_x"), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("getTotalPlaces: countByStatusNot(DELETED) 위임 (soft delete 제외)")
    void getTotalPlaces_excludesDeleted() {
        given(placeRepository.countByStatusNot(PlaceStatus.DELETED)).willReturn(42L);

        assertThat(service().getTotalPlaces()).isEqualTo(42L);
    }
}
