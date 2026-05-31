package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.ShopAccountRequest;
import com.chunbaetour.domain.shop.dto.response.ShopAccountResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 계좌 등록/변경 서비스 (KAN-187).
 * ShopWallet의 계좌 정보(bankName, accountNumber, accountHolder) 갱신.
 * 계좌는 가게 단위 — 정산 신청 시 해당 가게의 ShopWallet 계좌를 스냅샷으로 복사.
 *
 * [PENDING 정산 정책]
 * Settlement는 신청 시점에 계좌를 스냅샷하므로, PENDING 정산이 있는 상태에서 계좌를 변경해도
 * 진행 중인 정산의 입금 계좌에 영향을 주지 않는다. 변경된 계좌는 다음 정산 신청부터 적용된다.
 * 따라서 PENDING 중 계좌 변경을 허용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopAccountService {

    private final ShopRepository shopRepository;
    private final ShopWalletRepository shopWalletRepository;

    /**
     * 정산 계좌 등록/변경 (PUT — 전체 교체).
     * ACTIVE 상태 가게만 가능 — SUSPENDED/CLOSED 가게의 계좌 swap 우회 방지.
     * 기존 계좌 있으면 덮어쓰기, 없으면 신규 등록.
     */
    @Transactional
    public ShopAccountResponse updateAccount(Long userId, Long shopId, ShopAccountRequest request) {
        // 소유권 확인 — 타인 가게 접근 시 SHOP_001
        Shop shop = shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // ACTIVE 상태 가드 — SUSPENDED/CLOSED 가게의 정산 계좌 변경 차단
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }

        // ShopWallet 조회 — 가게 승인 시 자동 생성
        ShopWallet wallet = shopWalletRepository.findByShopId(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_WALLET_NOT_FOUND));

        // 계좌 전체 교체
        wallet.updateAccount(request.bankName(), request.accountNumber(), request.accountHolder());

        // 정산 계좌 변경 audit log — high-risk 행위 사후 추적용
        String accountTail = request.accountNumber().length() >= 4
                ? request.accountNumber().substring(request.accountNumber().length() - 4)
                : "****";
        log.info("[정산 계좌 변경] userId={}, shopId={}, bankName={}, holder={}, accountTail={}",
                userId, shopId, request.bankName(), request.accountHolder(), accountTail);

        return ShopAccountResponse.from(wallet);
    }
}
