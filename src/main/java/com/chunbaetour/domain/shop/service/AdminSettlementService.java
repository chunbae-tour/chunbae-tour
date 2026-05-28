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
 * 락 순서: Settlement → ShopWallet (데드락 방지)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSettlementService {

    private final SettlementRepository settlementRepository;
    private final ShopWalletRepository shopWalletRepository;

    /**
     * 정산 승인.
     * Settlement 비관적 락 → ShopWallet 비관적 락 순서 고정 (데드락 방지).
     * ShopWallet.debit()에서 잔액 부족 시 INSUFFICIENT_BALANCE 발생.
     * 실제 계좌 이체는 수동 처리 (시스템 외부).
     */
    @Transactional
    public void approveSettlement(Long settlementId) {
        // Settlement SELECT FOR UPDATE — 동시 승인/거절 직렬화
        Settlement settlement = settlementRepository.findByIdWithLock(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));

        // 상태 전이 가드 (PENDING만 처리 가능) — Settlement.approve() 내부에서도 검증
        settlement.approve();

        // ShopWallet SELECT FOR UPDATE — Settlement 락 이후 획득 (락 순서 준수)
        ShopWallet wallet = shopWalletRepository.findByShopIdWithLock(settlement.getShopId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_WALLET_NOT_FOUND));

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

        boolean hasNext = settlements.size() > size;
        List<Settlement> page = hasNext ? settlements.subList(0, size) : settlements;

        // 엔티티 → 응답 DTO 변환
        List<AdminSettlementResponse> content = page.stream()
                .map(AdminSettlementResponse::from)
                .toList();

        // 다음 커서: 마지막 항목 ID 인코딩
        String nextCursor = hasNext ? CursorUtils.encode(page.get(page.size() - 1).getId()) : null;
        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }
}
