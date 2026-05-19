package com.chunbaetour.domain.yeopjeon.event;

import com.chunbaetour.domain.auth.event.UserRegisteredEvent;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletEventListener {

    private final WalletService walletService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 회원가입 트랜젝션 커밋 완료 후에만 실행
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 지갑 생성을 별도의 새 트랜젝션에서 분리하여 처리 (회원가입과 별개로 진행)
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            walletService.createWallet(event.userId());
        } catch (Exception e) {
            log.error("Wallet creation failed for userId={}", event.userId(), e);
        }
    }
}
