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
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

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
}
