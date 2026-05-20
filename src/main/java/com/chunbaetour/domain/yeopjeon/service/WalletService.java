package com.chunbaetour.domain.yeopjeon.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.yeopjeon.dto.response.WalletBalanceResponse;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletBalanceResponse getWallet(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        return WalletBalanceResponse.from(wallet);
    }

    @Transactional
    public void createWallet(Long userId) {

        // 이미 지갑이 있는지 체크
        if (walletRepository.existsByUserId(userId)) {
            return;
        }

        // 없으면 저장 시도
        try {
            walletRepository.saveAndFlush(Wallet.create(userId));
            // DB 제약 위반 에러가 날 경우 잡음
        } catch (DataIntegrityViolationException e) {

            // 어떤 제약조건을 위반했는지, 이름이 uk_wallets_user_id인지 비교(맞으면 return)
            if (e.getCause() instanceof ConstraintViolationException cve
                    && "uk_wallets_user_id".equalsIgnoreCase(cve.getConstraintName())) {
                return;
            }

            // userId 중복이 아닌 다른 DB 에러일 경우 -> 위로 던짐
            throw e;
        }
    }
}
