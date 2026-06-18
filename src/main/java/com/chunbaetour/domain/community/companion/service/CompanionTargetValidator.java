package com.chunbaetour.domain.community.companion.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 동행 게시글 대상(targetType + targetId)의 실존 검증 (KAN-322).
 *
 * <p>다형 참조라 대상 도메인이 PLACE/MARKET/FESTIVAL로 갈리므로, targetType에 맞는 리포지토리로
 * 실제 존재 여부를 확인한다. 존재하지 않으면 도메인별 NOT_FOUND로 거부 → 지도에서 좌표를 못 받는
 * 깨진 동행글(고아 마커) 저장을 막는다.
 */
@Component
@RequiredArgsConstructor
public class CompanionTargetValidator {

    private final PlaceRepository placeRepository;
    private final TraditionalMarketRepository traditionalMarketRepository;
    private final FestivalRepository festivalRepository;

    public ValidatedCompanionTarget validate(CompanionTargetType targetType, Long targetId) {
        return switch (targetType) {
            case PLACE -> {
                Place place = placeRepository.findByIdAndStatus(targetId, PlaceStatus.ACTIVE)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
                yield new ValidatedCompanionTarget(targetType, targetId, place.getName());
            }
            case MARKET -> {
                TraditionalMarket market = traditionalMarketRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
                yield new ValidatedCompanionTarget(targetType, targetId, market.getName());
            }
            case FESTIVAL -> {
                Festival festival = festivalRepository.findByIdAndStatus(targetId, FestivalStatus.ACTIVE)
                        .orElseThrow(() -> new BusinessException(ErrorCode.FESTIVAL_NOT_FOUND));
                yield new ValidatedCompanionTarget(targetType, targetId, festival.getName());
            }
        };
    }
}
