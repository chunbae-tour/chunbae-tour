package com.chunbaetour.domain.festival.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.like.service.UserLikeService;
import com.chunbaetour.domain.like.type.LikeTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FestivalLikeServiceTest {

    @InjectMocks
    private FestivalLikeService festivalLikeService;

    @Mock
    private UserLikeService userLikeService;

    @Mock
    private FestivalRepository festivalRepository;

    @Test
    @DisplayName("removeLike — 비활성 축제도 기존 찜 취소는 허용")
    void removeLike_AllowsInactiveFestival() {
        // given
        Long userId = 1L;
        Long festivalId = 10L;
        given(festivalRepository.existsById(festivalId)).willReturn(true);
        given(userLikeService.removeLike(userId, LikeTargetType.FESTIVAL, festivalId)).willReturn(true);

        // when
        festivalLikeService.removeLike(userId, festivalId);

        // then
        verify(userLikeService).removeLike(userId, LikeTargetType.FESTIVAL, festivalId);
    }
}
