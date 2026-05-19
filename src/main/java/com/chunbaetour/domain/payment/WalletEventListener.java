package com.chunbaetour.domain.payment;

import com.chunbaetour.domain.auth.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletEventListener {

    private final WalletService walletService;

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        walletService.createWallet(event.userId());
    }
}
