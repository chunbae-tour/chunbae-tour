package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.shop.dto.response.AdminSettlementResponse;
import com.chunbaetour.domain.shop.entity.Settlement;
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.SettlementRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import com.chunbaetour.domain.shop.type.SettlementStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 정산 처리 서비스.
 * 정산 승인(ShopWallet 잔액 차감), 거절, 목록 조회 담당.
 * 락 순서: ShopWallet → Settlement (SettlementService.requestSettlement()와 동일, 데드락 방지)
 * approveSettlement()는 settlementId만 알고 shopId를 모르므로 비잠금 peek 후 ShopWallet 락 획득.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSettlementService {

    private final SettlementRepository settlementRepository;
    private final ShopWalletRepository shopWalletRepository;

    /**
     * 정산 승인.
     * 락 순서: ShopWallet → Settlement (SettlementService.requestSettlement()와 동일, 데드락 방지).
     * settlementId만 알고 shopId를 모르는 문제는 비잠금 peek으로 shopId 선조회 후 락 획득.
     * ShopWallet.debit()에서 잔액 부족 시 INSUFFICIENT_BALANCE 발생.
     * 실제 계좌 이체는 수동 처리 (시스템 외부).
     */
    @Transactional
    public void approveSettlement(Long settlementId) {
        // 비잠금 peek — shopId 취득 목적, 상태는 이후 재조회로 검증
        Settlement peek = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));

        // ShopWallet SELECT FOR UPDATE 먼저 획득 (락 순서 1번: ShopWallet)
        ShopWallet wallet = shopWalletRepository.findByShopIdWithLock(peek.getShopId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_WALLET_NOT_FOUND));

        // Settlement SELECT FOR UPDATE — ShopWallet 락 이후 획득 (락 순서 2번: Settlement)
        // 비잠금 peek 이후 상태가 바뀌었을 수 있으므로 재조회 필수
        Settlement settlement = settlementRepository.findByIdWithLock(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));

        // 상태 전이 가드 (PENDING만 처리 가능) — Settlement.approve() 내부에서도 검증
        settlement.approve();

        // 신청 시점 스냅샷 금액 차감 — 잔액 부족 시 INSUFFICIENT_BALANCE
        wallet.debit(settlement.getAmount());
    }

    /**
     * 정산 거절.
     * Settlement 비관적 락으로 조회 후 상태 전이.
     */
    @Transactional
    public void rejectSettlement(Long settlementId, String reason) {
        // Settlement SELECT FOR UPDATE
        Settlement settlement = settlementRepository.findByIdWithLock(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));

        // 상태 전이 가드 (PENDING만 거절 가능)
        settlement.reject(reason);
    }

    /**
     * 관리자 정산 목록 조회 — cursor keyset 페이징 (id DESC).
     * status=null이면 전체 조회, status 지정 시 해당 상태만 필터링.
     */
    public CursorPageResponse<AdminSettlementResponse> getSettlements(
            String cursor, int size, SettlementStatus status) {
        // cursor 디코딩 — null이면 첫 페이지
        Long cursorId = CursorUtils.decodeSafe(cursor);

        // size+1 조회로 다음 페이지 존재 여부 판별
        List<Settlement> settlements = settlementRepository.findAllWithCursor(
                cursorId, status, PageRequest.of(0, size + 1));

        // 다음 페이지 판별·매핑·커서 인코딩을 공통 팩토리로 위임 (KAN-295)
        return CursorPageResponse.of(settlements, size, AdminSettlementResponse::from, Settlement::getId);
    }
}
