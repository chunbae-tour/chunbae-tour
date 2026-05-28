package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.AdApplicationRequest;
import com.chunbaetour.domain.shop.dto.response.AdApplicationResponse;
import com.chunbaetour.domain.shop.entity.AdApplication;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.AdApplicationRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.AdApplicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상인 광고 신청 서비스.
 * 광고 신청 생성 및 중복 방지 담당.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdApplicationService {

    private final AdApplicationRepository adApplicationRepository;
    private final ShopRepository shopRepository;

    /**
     * 광고 신청.
     * 본인 가게 확인 → 중복 PENDING 차단 → AdApplication 생성.
     */
    @Transactional
    public AdApplicationResponse applyAd(Long userId, AdApplicationRequest request) {
        // shopId + userId 조합으로 본인 가게 조회
        Shop shop = shopRepository.findByIdAndUserId(request.shopId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // 날짜 유효성 — 시작일이 종료일보다 늦으면 거부
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // PENDING 중복 체크 — SELECT FOR UPDATE로 current read 보장
        if (!adApplicationRepository.findByShopIdAndStatusWithLock(shop.getId(), AdApplicationStatus.PENDING).isEmpty()) {
            throw new BusinessException(ErrorCode.DUPLICATE_AD_APPLICATION);
        }

        AdApplication application = AdApplication.create(
                shop.getId(),
                request.adType(),
                request.startDate(),
                request.endDate(),
                request.cost()
        );
        AdApplication saved = adApplicationRepository.save(application);

        return AdApplicationResponse.from(saved);
    }
}
