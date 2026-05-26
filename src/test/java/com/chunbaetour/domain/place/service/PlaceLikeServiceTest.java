package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.UserLike;
import com.chunbaetour.domain.place.dto.response.UserLikedPlaceResponse;
import com.chunbaetour.domain.place.repository.UserLikeRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceLikeServiceTest {

    @Mock
    private UserLikeRepository userLikeRepository;

    @InjectMocks
    private PlaceLikeService placeLikeService;

    @Test
    @DisplayName("마이페이지 연동 - 정상 페이징 찜 목록 조회")
    void getUserLikedPlaces_Success() {
        // given
        Long userId = 1L;
        PageRequest pageRequest = PageRequest.of(0, 10);
        
        Account mockUser = mock(Account.class);
        Place mockPlace = mock(Place.class);
        
        when(mockPlace.getId()).thenReturn(100L);
        when(mockPlace.getName()).thenReturn("제주 바다");
        when(mockPlace.getCategory()).thenReturn(PlaceCategory.TOURIST_SPOT);
        
        UserLike userLike = UserLike.of(mockUser, mockPlace);
        Page<UserLike> mockPage = new PageImpl<>(List.of(userLike));
        
        when(userLikeRepository.findByUserIdAndPlace_Status(eq(userId), eq(PlaceStatus.ACTIVE), eq(pageRequest)))
                .thenReturn(mockPage);

        // when
        Page<UserLikedPlaceResponse> result = placeLikeService.getUserLikedPlaces(userId, pageRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).placeId()).isEqualTo(100L);
        assertThat(result.getContent().get(0).name()).isEqualTo("제주 바다");
        verify(userLikeRepository).findByUserIdAndPlace_Status(userId, PlaceStatus.ACTIVE, pageRequest);
    }

    @Test
    @DisplayName("마이페이지 연동 - 찜 0건 빈 결과 반환")
    void getUserLikedPlaces_EmptyResult() {
        // given
        Long userId = 1L;
        PageRequest pageRequest = PageRequest.of(0, 10);
        
        when(userLikeRepository.findByUserIdAndPlace_Status(eq(userId), eq(PlaceStatus.ACTIVE), eq(pageRequest)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // when
        Page<UserLikedPlaceResponse> result = placeLikeService.getUserLikedPlaces(userId, pageRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("마이페이지 연동 - page size가 100을 초과하면 예외 발생")
    void getUserLikedPlaces_ExceedMaxPageSize() {
        // given
        Long userId = 1L;
        PageRequest pageRequest = PageRequest.of(0, 101);

        // when & then
        assertThatThrownBy(() -> placeLikeService.getUserLikedPlaces(userId, pageRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }
}
