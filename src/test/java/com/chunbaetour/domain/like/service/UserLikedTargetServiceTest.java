package com.chunbaetour.domain.like.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.like.dto.response.UserLikedTargetResponse;
import com.chunbaetour.domain.like.repository.UserLikedTargetQueryRepository;
import com.chunbaetour.domain.like.type.LikeTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class UserLikedTargetServiceTest {

    @InjectMocks
    private UserLikedTargetService userLikedTargetService;

    @Mock
    private UserLikedTargetQueryRepository queryRepository;

    @Test
    @DisplayName("PLACE liked list delegates to place query")
    void getLikedTargets_Place() {
        // given
        Long userId = 1L;
        PageRequest pageable = PageRequest.of(0, 20);
        given(queryRepository.findLikedPlaces(userId, pageable))
                .willReturn(new PageImpl<UserLikedTargetResponse>(java.util.List.of()));

        // when
        userLikedTargetService.getLikedTargets(userId, LikeTargetType.PLACE, pageable);

        // then
        verify(queryRepository).findLikedPlaces(userId, pageable);
    }

    @Test
    @DisplayName("MARKET liked list delegates to market query")
    void getLikedTargets_Market() {
        // given
        Long userId = 1L;
        PageRequest pageable = PageRequest.of(0, 20);
        given(queryRepository.findLikedMarkets(userId, pageable))
                .willReturn(new PageImpl<UserLikedTargetResponse>(java.util.List.of()));

        // when
        Page<?> result = userLikedTargetService.getLikedTargets(userId, LikeTargetType.MARKET, pageable);

        // then
        verify(queryRepository).findLikedMarkets(userId, pageable);
        org.assertj.core.api.Assertions.assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("FESTIVAL liked list delegates to festival query")
    void getLikedTargets_Festival() {
        // given
        Long userId = 1L;
        PageRequest pageable = PageRequest.of(0, 20);
        given(queryRepository.findLikedFestivals(userId, pageable))
                .willReturn(new PageImpl<UserLikedTargetResponse>(java.util.List.of()));

        // when
        userLikedTargetService.getLikedTargets(userId, LikeTargetType.FESTIVAL, pageable);

        // then
        verify(queryRepository).findLikedFestivals(userId, pageable);
    }

    @Test
    @DisplayName("page size over 100 is rejected")
    void getLikedTargets_RejectsTooLargePage() {
        assertThatThrownBy(() -> userLikedTargetService.getLikedTargets(
                1L,
                LikeTargetType.PLACE,
                PageRequest.of(0, 101)
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }
}
