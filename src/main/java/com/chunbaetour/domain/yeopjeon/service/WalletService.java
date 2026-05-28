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

/**
 * 엽전 지갑(Wallet) 서비스
 * <p>
 * 담당 기능: 잔액 조회, PG 결제 완료 후 엽전 충전, 신규 유저 지갑 생성.
 * charge()는 CallbackService(PG 웹훅 처리)에서 호출되며,
 * SELECT FOR UPDATE로 동시 충전 시 잔액 정합성을 보장.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletService {

    private static final String UK_WALLETS_USER_ID = "uk_wallets_user_id";

    private final WalletRepository walletRepository;
    private final YeopjeonHistoryRepository yeopjeonHistoryRepository;

    /** 내 엽전 잔액 조회. 지갑 없으면 PAY_012(WALLET_NOT_FOUND) 반환. */
    public WalletBalanceResponse getWallet(Long userId) {
        // DB에서 유저 지갑 조회 (없으면 예외)
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        // 잔액 정보를 응답 DTO로 변환하여 반환
        return WalletBalanceResponse.from(wallet);
    }

    /**
     * PG 결제 완료 후 엽전 충전.
     * SELECT FOR UPDATE(비관적 락)로 지갑 조회 후 잔액 증가 + 충전 이력 저장.
     * 락 획득 순서: PaymentOrder → Wallet (호출자가 반드시 이 순서를 지켜야 데드락 방지).
     */
    @Transactional
    public void charge(Long userId, Long amount, Long paymentOrderId) {
        // SELECT FOR UPDATE로 지갑 행 락 획득 (동시 충전 시 잔액 정합성 보장)
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        // 잔액 증가
        wallet.credit(amount);
        // 충전 이력 DB 저장 (balanceSnapshot = credit 후 잔액)
        yeopjeonHistoryRepository.save(
                YeopjeonHistory.ofCharge(userId, amount, wallet.getBalance(), paymentOrderId)
        );
    }

    /**
     * 환불 승인 시 엽전 회수(차감) + 환불 이력 저장.
     * 충전과 반대 방향: PG가 실제 돈을 카드로 돌려주고, 앱은 엽전을 회수한다.
     * 락 획득 순서: Refund → PaymentOrder → Wallet (호출자 AdminRefundService가 준수해야 데드락 방지).
     */
    @Transactional
    public void reclaimForRefund(Long userId, Long amount, Long paymentOrderId) {
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

    /**
     * 스토어 상품 구매 시 엽전 차감 + 구매 이력 저장.
     * SELECT FOR UPDATE로 지갑 행 락 획득 후 잔액 차감.
     * 락 획득 순서: Product → Wallet (StorePurchaseService에서 Product 락 먼저 획득).
     */
    @Transactional
    public void spendForPurchase(Long userId, long amount, String productName) {
        // SELECT FOR UPDATE로 지갑 행 락 획득
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        // 잔액 차감 (부족 시 Wallet.debit()에서 INSUFFICIENT_BALANCE 던짐)
        wallet.debit(amount);
        // 구매 이력 저장
        yeopjeonHistoryRepository.save(
                YeopjeonHistory.ofStorePurchase(userId, amount, wallet.getBalance(), productName)
        );
    }

    /**
     * 신규 유저 지갑 생성 (멱등).
     * existsByUserId 체크 후 없으면 saveAndFlush.
     * 동시 요청으로 UK_WALLETS_USER_ID 제약 위반 시 정상 처리(return) — 다른 DB 에러는 그대로 던짐.
     */
    @Transactional
    public void createWallet(Long userId) {
        // 이미 지갑이 존재하면 중복 생성 방지
        if (walletRepository.existsByUserId(userId)) {
            return;
        }
        try {
            // 새 지갑 엔티티 생성 후 DB에 즉시 저장(flush)
            walletRepository.saveAndFlush(Wallet.create(userId));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 유니크 제약(uk_wallets_user_id) 위반 시 → 이미 생성된 것으로 간주하고 정상 처리
            if (e.getCause() instanceof ConstraintViolationException cve
                    && UK_WALLETS_USER_ID.equalsIgnoreCase(cve.getConstraintName())) {
                return;
            }
            // userId 중복 외 다른 DB 에러는 상위로 전파
            throw e;
        }
    }
}
