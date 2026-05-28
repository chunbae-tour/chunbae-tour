package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.shop.dto.response.SettlementResponse;
import com.chunbaetour.domain.shop.entity.Settlement;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.SettlementRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import com.chunbaetour.domain.shop.type.SettlementStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 상인 정산 서비스.
 * 정산 신청, 내 정산 내역 조회 담당.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private static final long MIN_SETTLEMENT_AMOUNT = 5_000L;

    private final SettlementRepository settlementRepository;
    private final ShopRepository shopRepository;
    private final ShopWalletRepository shopWalletRepository;

    /**
     * 정산 신청.
     * 잔액 > 0 확인 → 중복 PENDING 차단 → ShopWallet 잔액 + 계좌 스냅샷 → Settlement 생성.
     * 실제 잔액 차감은 관리자 승인(AdminSettlementService) 시점에 처리.
     */
    @Transactional
    public SettlementResponse requestSettlement(Long userId) {
        // userId로 상인 가게 조회
        Shop shop = shopRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // 이미 PENDING 정산 신청 존재 시 중복 차단

        // ShopWallet 조회 — 잔액 및 계좌 정보 스냅샷용
        ShopWallet wallet = shopWalletRepository.findByShopIdWithLock(shop.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_WALLET_NOT_FOUND));

        // PENDING 중복 체크 — SELECT FOR UPDATE로 current read 보장 (REPEATABLE READ 스냅샷 우회)
        if (!settlementRepository.findByShopIdAndStatusWithLock(shop.getId(), SettlementStatus.PENDING).isEmpty()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SETTLEMENT_REQUEST);
        }

        // 정산 가능 잔액 없으면 차단
        if (wallet.getBalance() <= 0) {
            throw new BusinessException(ErrorCode.SETTLEMENT_BALANCE_EMPTY);
        }
        // 최소 정산 금액(5,000엽전) 미달 차단
        if (wallet.getBalance() < MIN_SETTLEMENT_AMOUNT) {
            throw new BusinessException(ErrorCode.SETTLEMENT_AMOUNT_TOO_LOW);
        }

        // 계좌 정보 미설정 시 DB 예외 대신 명시적 BusinessException
        if (!StringUtils.hasText(wallet.getBankName())
                || !StringUtils.hasText(wallet.getAccountNumber())
                || !StringUtils.hasText(wallet.getAccountHolder())) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        // 신청 시점 잔액 + 계좌 정보 스냅샷으로 정산 요청 생성
        Settlement settlement = Settlement.create(
                shop.getId(),
                wallet.getBalance(),
                wallet.getBankName(),
                wallet.getAccountNumber(),
                wallet.getAccountHolder()
        );
        Settlement saved = settlementRepository.save(settlement);

        return SettlementResponse.from(saved);
    }

    /** 내 정산 내역 조회 — cursor keyset 페이징 (id DESC) */
    public CursorPageResponse<SettlementResponse> getMySettlements(Long userId, String cursor, int size) {
        // userId로 가게 조회
        Shop shop = shopRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // cursor 디코딩 — null이면 첫 페이지
        Long cursorId = CursorUtils.decodeSafe(cursor);

        // size+1 조회로 다음 페이지 존재 여부 판별
        List<Settlement> settlements = settlementRepository.findByShopId(
                shop.getId(), cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = settlements.size() > size;
        List<Settlement> page = hasNext ? settlements.subList(0, size) : settlements;

        // 엔티티 → 응답 DTO 변환
        List<SettlementResponse> content = page.stream()
                .map(SettlementResponse::from)
                .toList();

        // 다음 커서: 마지막 항목 ID 인코딩
        String nextCursor = hasNext ? CursorUtils.encode(page.get(page.size() - 1).getId()) : null;
        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }
}
