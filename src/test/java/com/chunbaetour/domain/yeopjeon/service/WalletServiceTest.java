package com.chunbaetour.domain.yeopjeon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.yeopjeon.dto.response.WalletBalanceResponse;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.entity.YeopjeonHistory;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import com.chunbaetour.domain.yeopjeon.type.YeopjeonHistoryType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private YeopjeonHistoryRepository yeopjeonHistoryRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    @DisplayName("존재하는 userId로 조회 시 잔액 0을 반환한다")
    void getWallet_success_returns_balance() {
        Wallet wallet = Wallet.create(1L);
        given(walletRepository.findByUserId(1L)).willReturn(Optional.of(wallet));

        WalletBalanceResponse response = walletService.getWallet(1L);

        assertThat(response.balance()).isEqualTo(0L);
    }

    @Test
    @DisplayName("존재하지 않는 userId 조회 시 PAY_012(WALLET_NOT_FOUND)를 던진다")
    void getWallet_notFound_throws_PAY_012() {
        given(walletRepository.findByUserId(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getWallet(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WALLET_NOT_FOUND);
    }

    @Test
    @DisplayName("지갑이 없는 사용자에게 새 지갑을 생성하고 저장한다")
    void createWallet_success_saves_new_wallet() {
        given(walletRepository.existsByUserId(1L)).willReturn(false);

        walletService.createWallet(1L);

        verify(walletRepository).saveAndFlush(any(Wallet.class));
    }

    @Test
    @DisplayName("이미 지갑이 있는 사용자는 저장 없이 그냥 반환한다")
    void createWallet_already_exists_skips_save() {
        given(walletRepository.existsByUserId(1L)).willReturn(true);

        walletService.createWallet(1L);

        verify(walletRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("charge 정상: 잔액 증가 + 이력 저장")
    void charge_success_credits_balance_and_saves_history() {
        Wallet wallet = Wallet.create(1L);
        given(walletRepository.findByUserIdWithLock(1L)).willReturn(Optional.of(wallet));
        given(yeopjeonHistoryRepository.save(any(YeopjeonHistory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        walletService.charge(1L, 10_000L, 42L);

        assertThat(wallet.getBalance()).isEqualTo(10_000L);
        verify(yeopjeonHistoryRepository).save(any(YeopjeonHistory.class));
    }

    @Test
    @DisplayName("charge: 지갑 없으면 PAY_012(WALLET_NOT_FOUND) 던진다")
    void charge_wallet_not_found_throws_PAY_012() {
        given(walletRepository.findByUserIdWithLock(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.charge(99L, 10_000L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WALLET_NOT_FOUND);
    }

    @Test
    @DisplayName("spendForPurchase 정상: 잔액 차감 + 스토어 구매 이력 저장")
    void spendForPurchase_success_debits_balance_and_saves_store_purchase_history() {
        Wallet wallet = Wallet.create(1L);
        wallet.credit(10_000L);
        given(walletRepository.findByUserIdWithLock(1L)).willReturn(Optional.of(wallet));
        given(yeopjeonHistoryRepository.save(any(YeopjeonHistory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        walletService.spendForPurchase(1L, 3_000L, "테스트 상품");

        ArgumentCaptor<YeopjeonHistory> captor = ArgumentCaptor.forClass(YeopjeonHistory.class);
        verify(yeopjeonHistoryRepository).save(captor.capture());
        YeopjeonHistory history = captor.getValue();

        assertThat(wallet.getBalance()).isEqualTo(7_000L);
        assertThat(history.getUserId()).isEqualTo(1L);
        assertThat(history.getPaymentOrderId()).isNull();
        assertThat(history.getShopId()).isNull();
        assertThat(history.getType()).isEqualTo(YeopjeonHistoryType.PAYMENT);
        assertThat(history.getAmount()).isEqualTo(3_000L);
        assertThat(history.getBalanceSnapshot()).isEqualTo(7_000L);
        assertThat(history.getDescription()).isEqualTo("스토어 구매 - 테스트 상품");
    }
}
