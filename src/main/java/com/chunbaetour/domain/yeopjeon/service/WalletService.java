package com.chunbaetour.domain.yeopjeon.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.yeopjeon.dto.response.WalletBalanceResponse;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.entity.YeopjeonHistory;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
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
    private final YeopjeonHistoryRepository yeopjeonHistoryRepository;

    public WalletBalanceResponse getWallet(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        return WalletBalanceResponse.from(wallet);
    }

    @Transactional
    public void charge(Long userId, Long amount, Long paymentOrderId) {
        // 락 획득 순서: PaymentOrder → Wallet (호출자가 반드시 이 순서를 지켜야 데드락 방지)
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        wallet.credit(amount);
        yeopjeonHistoryRepository.save(
                YeopjeonHistory.ofCharge(userId, amount, wallet.getBalance(), paymentOrderId)
        );
    }

    /**
     * 환불 승인 시 엽전 차감 + 환불 이력 저장.
     * 락 획득 순서: Refund → PaymentOrder → Wallet (호출자 AdminRefundService가 준수해야 데드락 방지).
     */
    @Transactional
    public void refund(Long userId, Long amount, Long paymentOrderId) {
        // SELECT FOR UPDATE로 지갑 행 락 획득
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        // 잔액 확인 (충전 후 소비했을 경우 부족 가능)
        if (wallet.getBalance() < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        // 엽전 차감
        wallet.debit(amount);
        // 환불 이력 DB 저장 (balanceSnapshot = debit 후 잔액)
        yeopjeonHistoryRepository.save(
                YeopjeonHistory.ofRefund(userId, amount, wallet.getBalance(), paymentOrderId)
        );
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
