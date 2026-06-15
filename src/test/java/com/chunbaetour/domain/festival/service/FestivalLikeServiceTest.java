package com.chunbaetour.domain.festival.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import com.chunbaetour.domain.like.service.UserLikeService;
import com.chunbaetour.domain.like.type.LikeTargetType;
import java.time.LocalDate;
import java.util.Optional;
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
    @DisplayName("inactive festival like can still be removed")
    void removeLike_AllowsInactiveFestival() {
        // given
        Long userId = 1L;
        Long festivalId = 10L;
        Festival hiddenFestival = Festival.create(
                "hidden festival",
                "description",
                "서울",
                "address",
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                null,
                null,
                FestivalStatus.HIDDEN
        );
        given(festivalRepository.findById(festivalId)).willReturn(Optional.of(hiddenFestival));
        given(userLikeService.removeLike(userId, LikeTargetType.FESTIVAL, festivalId)).willReturn(true);

        // when
        festivalLikeService.removeLike(userId, festivalId);

        // then
        verify(userLikeService).removeLike(userId, LikeTargetType.FESTIVAL, festivalId);
    }
}
