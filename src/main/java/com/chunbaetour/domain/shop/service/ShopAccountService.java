package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.ShopAccountRequest;
import com.chunbaetour.domain.shop.dto.response.ShopAccountResponse;
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 계좌 등록/변경 서비스 (KAN-187).
 * ShopWallet의 계좌 정보(bankName, accountNumber, accountHolder) 갱신.
 * 계좌는 가게 단위 — 정산 신청 시 해당 가게의 ShopWallet 계좌를 스냅샷.
 */
@Service
@RequiredArgsConstructor
public class ShopAccountService {

    private final ShopRepository shopRepository;
    private final ShopWalletRepository shopWalletRepository;

    /**
     * 정산 계좌 등록/변경 (PUT — 전체 교체).
     * 소유권 확인 후 ShopWallet 계좌 정보 갱신.
     * 기존 계좌 있으면 덮어쓰기, 없으면 신규 등록.
     */
    @Transactional
    public ShopAccountResponse updateAccount(Long userId, Long shopId, ShopAccountRequest request) {
        // 소유권 확인 — 타인 가게 접근 시 SHOP_001
        shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // ShopWallet 조회 — 가게 승인 시 자동 생성
        ShopWallet wallet = shopWalletRepository.findByShopId(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_WALLET_NOT_FOUND));

        // 계좌 전체 교체
        wallet.updateAccount(request.bankName(), request.accountNumber(), request.accountHolder());

        return ShopAccountResponse.from(wallet);
    }
}
