package com.chunbaetour.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.dto.WalletResponse;
import java.util.Optional;
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
    void getWallet_success_returns_balance() {
        Wallet wallet = Wallet.create(1L);
        given(walletRepository.findByUserId(1L)).willReturn(Optional.of(wallet));

        WalletResponse response = walletService.getWallet(1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualTo(0L);
    }

    @Test
    void getWallet_notFound_throws_PAY_012() {
        given(walletRepository.findByUserId(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getWallet(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WALLET_NOT_FOUND);
    }

    @Test
    void createWallet_success_saves_new_wallet() {
        given(walletRepository.existsByUserId(1L)).willReturn(false);

        walletService.createWallet(1L);

        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void createWallet_already_exists_skips_save() {
        given(walletRepository.existsByUserId(1L)).willReturn(true);

        walletService.createWallet(1L);

        verify(walletRepository, never()).save(any());
    }
}
