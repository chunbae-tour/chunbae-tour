package com.chunbaetour.domain.payment;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.dto.WalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        return WalletResponse.from(wallet);
    }

    @Transactional
    public void createWallet(Long userId) {
        if (walletRepository.existsByUserId(userId)) {
            return;
        }
        walletRepository.save(Wallet.create(userId));
    }
}
