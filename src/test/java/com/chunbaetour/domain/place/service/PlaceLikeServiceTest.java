package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.like.service.UserLikeService;
import com.chunbaetour.domain.like.type.LikeTargetType;
import com.chunbaetour.domain.place.dto.response.UserLikedPlaceResponse;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import com.chunbaetour.domain.place.event.PlaceLikeChangedEvent;

@ExtendWith(MockitoExtension.class)
class PlaceLikeServiceTest {

    @Mock
    private UserLikeService userLikeService;
    
    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceQueryRepository placeQueryRepository;

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PlaceLikeService placeLikeService;

    @Test
    @DisplayName("마이페이지 연동 - 정상 페이징 찜 목록 조회")
    void getUserLikedPlaces_Success() {
        // given
        Long userId = 1L;
        PageRequest pageRequest = PageRequest.of(0, 10);
        
        UserLikedPlaceResponse mockResponse = UserLikedPlaceResponse.builder()
                .placeId(100L)
                .name("제주 바다")
                .category(PlaceCategory.TOURIST_SPOT)
                .build();
        
        Page<UserLikedPlaceResponse> mockPage = new PageImpl<>(List.of(mockResponse));
        
        when(placeQueryRepository.findUserLikedPlaces(eq(userId), eq(pageRequest)))
                .thenReturn(mockPage);

        // when
        Page<UserLikedPlaceResponse> result = placeLikeService.getUserLikedPlaces(userId, pageRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).placeId()).isEqualTo(100L);
        assertThat(result.getContent().get(0).name()).isEqualTo("제주 바다");
        verify(placeQueryRepository).findUserLikedPlaces(userId, pageRequest);
    }

    @Test
    @DisplayName("마이페이지 연동 - 찜 0건 빈 결과 반환")
    void getUserLikedPlaces_EmptyResult() {
        // given
        Long userId = 1L;
        PageRequest pageRequest = PageRequest.of(0, 10);
        
        when(placeQueryRepository.findUserLikedPlaces(eq(userId), eq(pageRequest)))
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

    @Test
    @DisplayName("찜 추가 시 PlaceLikeChangedEvent 이벤트가 발행된다")
    void addLike_PublishesEvent() {
        // given
        Long userId = 1L;
        Long placeId = 100L;
        Place mockPlace = mock(Place.class);
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(mockPlace));

        // when
        placeLikeService.addLike(userId, placeId);

        // then
        verify(userLikeService).addLike(userId, LikeTargetType.PLACE, placeId);
        verify(eventPublisher).publishEvent(any(PlaceLikeChangedEvent.class));
    }

    @Test
    @DisplayName("찜 취소 시 PlaceLikeChangedEvent 이벤트가 발행된다")
    void removeLike_PublishesEvent() {
        // given
        Long userId = 1L;
        Long placeId = 100L;
        Place mockPlace = mock(Place.class);
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(mockPlace));

        // when
        placeLikeService.removeLike(userId, placeId);

        // then
        verify(userLikeService).removeLike(userId, LikeTargetType.PLACE, placeId);
        verify(eventPublisher).publishEvent(any(PlaceLikeChangedEvent.class));
    }
}
