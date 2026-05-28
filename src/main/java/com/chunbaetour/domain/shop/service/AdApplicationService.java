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
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상인 광고 신청 서비스.
 * 광고 신청 생성, 중복 방지, 광고 연장 담당.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdApplicationService {

    /** 광고 연장 분산 락 키: ad:extend:lock:{adId} — 동일 광고 동시 연장 직렬화 */
    private static final String AD_EXTEND_LOCK_KEY = "ad:extend:lock:%d";
    private static final int LOCK_WAIT_SECONDS = 3;
    /** leaseTime 명시 — watchdog 무한 갱신 방지 (설계서 §6.2: 장애 시 자동 해제 보장) */
    private static final int LOCK_LEASE_SECONDS = 5;

    private final AdApplicationRepository adApplicationRepository;
    private final ShopRepository shopRepository;
    private final WalletService walletService;
    private final RedissonClient redissonClient;

    /**
     * 광고 신청.
     * 본인 가게 확인 → 중복 PENDING 차단 → AdApplication 생성.
     */
    @Transactional
    public AdApplicationResponse applyAd(Long userId, AdApplicationRequest request) {
        // shopId + userId 조합으로 본인 가게 조회
        Shop shop = shopRepository.findByIdAndUserId(request.shopId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // 날짜 유효성 — 과거 시작일 또는 시작일이 종료일보다 늦으면 거부
        if (request.startDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
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

    /**
     * 광고 연장.
     * Redisson 분산 락 → AdApplication SELECT FOR UPDATE → 비용 계산 → 엽전 차감 → endDate 연장.
     * 락 순서: AdApplication → Wallet (WalletService.spendForAdExtension()과 동일, 데드락 방지).
     */
    @Transactional
    public AdApplicationResponse extendAd(Long userId, Long adId, int extensionDays) {
        RLock lock = redissonClient.getLock(AD_EXTEND_LOCK_KEY.formatted(adId));
        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
            }

            // 비잠금 peek — 소유권 검증 목적 (shopId 취득)
            AdApplication peek = adApplicationRepository.findById(adId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AD_APPLICATION_NOT_FOUND));

            // 본인 가게 소유 확인
            shopRepository.findByIdAndUserId(peek.getShopId(), userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

            // AdApplication SELECT FOR UPDATE — 상태 전이 경쟁 방지
            AdApplication application = adApplicationRepository.findByIdWithLock(adId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AD_APPLICATION_NOT_FOUND));

            // 연장 비용 계산 — 원래 일 단가 × extensionDays
            long extensionCost = application.calculateExtensionCost(extensionDays);

            // 엽전 차감 — SELECT FOR UPDATE on Wallet (락 순서 2번: Wallet)
            walletService.spendForAdExtension(userId, extensionCost, application.getAdType());

            // endDate 연장 — APPROVED 상태 아니면 AD_APPLICATION_INVALID_STATUS
            application.extend(extensionDays);

            return AdApplicationResponse.from(application);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        } finally {
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (Exception e) {
                log.warn("[광고 연장] 분산 락 해제 실패 (adId: {})", adId, e);
            }
        }
    }
}
