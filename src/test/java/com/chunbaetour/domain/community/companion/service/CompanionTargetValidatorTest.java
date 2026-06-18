package com.chunbaetour.domain.community.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.community.companion.entity.CompanionTargetType;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanionTargetValidatorTest {

    @Mock PlaceRepository placeRepository;
    @Mock TraditionalMarketRepository traditionalMarketRepository;
    @Mock FestivalRepository festivalRepository;
    @InjectMocks CompanionTargetValidator validator;

    @Test
    void place는_ACTIVE만_대상으로_허용하고_실제이름을_반환() {
        Place place = org.mockito.Mockito.mock(Place.class);
        given(place.getName()).willReturn("서버장소명");
        given(placeRepository.findByIdAndStatus(100L, PlaceStatus.ACTIVE)).willReturn(Optional.of(place));

        ValidatedCompanionTarget target = validator.validate(CompanionTargetType.PLACE, 100L);

        assertThat(target.targetName()).isEqualTo("서버장소명");
        then(placeRepository).should().findByIdAndStatus(100L, PlaceStatus.ACTIVE);
    }

    @Test
    void place가_ACTIVE가_아니면_PLACE_NOT_FOUND() {
        given(placeRepository.findByIdAndStatus(100L, PlaceStatus.ACTIVE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(CompanionTargetType.PLACE, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    void festival은_ACTIVE만_대상으로_허용하고_실제이름을_반환() {
        Festival festival = org.mockito.Mockito.mock(Festival.class);
        given(festival.getName()).willReturn("서버축제명");
        given(festivalRepository.findByIdAndStatus(200L, FestivalStatus.ACTIVE)).willReturn(Optional.of(festival));

        ValidatedCompanionTarget target = validator.validate(CompanionTargetType.FESTIVAL, 200L);

        assertThat(target.targetName()).isEqualTo("서버축제명");
        then(festivalRepository).should().findByIdAndStatus(200L, FestivalStatus.ACTIVE);
    }

    @Test
    void festival이_ACTIVE가_아니면_FESTIVAL_NOT_FOUND() {
        given(festivalRepository.findByIdAndStatus(200L, FestivalStatus.ACTIVE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(CompanionTargetType.FESTIVAL, 200L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FESTIVAL_NOT_FOUND);
    }

    @Test
    void market은_soft_delete가_없어_findById로_검증하고_실제이름을_반환() {
        TraditionalMarket market = org.mockito.Mockito.mock(TraditionalMarket.class);
        given(market.getName()).willReturn("서버시장명");
        given(traditionalMarketRepository.findById(55L)).willReturn(Optional.of(market));

        ValidatedCompanionTarget target = validator.validate(CompanionTargetType.MARKET, 55L);

        assertThat(target.targetName()).isEqualTo("서버시장명");
        then(traditionalMarketRepository).should().findById(55L);
    }

    @Test
    void market이_없으면_MARKET_NOT_FOUND() {
        given(traditionalMarketRepository.findById(55L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(CompanionTargetType.MARKET, 55L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MARKET_NOT_FOUND);
    }
}
