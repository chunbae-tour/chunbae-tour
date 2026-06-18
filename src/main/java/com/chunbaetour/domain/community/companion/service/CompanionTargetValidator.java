package com.chunbaetour.domain.community.companion.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.community.companion.entity.CompanionTargetType;
import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import com.chunbaetour.domain.place.repository.PlaceRepository;
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

    public void validateExists(CompanionTargetType targetType, Long targetId) {
        boolean exists = switch (targetType) {
            case PLACE -> placeRepository.existsById(targetId);
            case MARKET -> traditionalMarketRepository.existsById(targetId);
            case FESTIVAL -> festivalRepository.existsById(targetId);
        };
        if (!exists) {
            throw new BusinessException(switch (targetType) {
                case PLACE -> ErrorCode.PLACE_NOT_FOUND;
                case MARKET -> ErrorCode.MARKET_NOT_FOUND;
                case FESTIVAL -> ErrorCode.FESTIVAL_NOT_FOUND;
            });
        }
    }
}
