package com.chunbaetour.domain.yeopjeon;

import com.chunbaetour.domain.auth.event.UserRegisteredEvent;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
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
