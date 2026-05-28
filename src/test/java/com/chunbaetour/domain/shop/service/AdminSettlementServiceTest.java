package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.shop.dto.response.AdminSettlementResponse;
import com.chunbaetour.domain.shop.entity.Settlement;
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.SettlementRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import com.chunbaetour.domain.shop.type.SettlementStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminSettlementServiceTest {

    @Mock private SettlementRepository settlementRepository;
    @Mock private ShopWalletRepository shopWalletRepository;

    @InjectMocks
    private AdminSettlementService adminSettlementService;

    private static final Long SETTLEMENT_ID = 1L;
    private static final Long SHOP_ID = 10L;
    private static final long AMOUNT = 50_000L;

    private Settlement createSettlement(SettlementStatus status) {
        Settlement s = Settlement.create(SHOP_ID, AMOUNT, "국민은행", "123-456-789", "홍길동");
        ReflectionTestUtils.setField(s, "id", SETTLEMENT_ID);
        ReflectionTestUtils.setField(s, "status", status);
        return s;
    }

    private ShopWallet createWallet(long balance) {
        ShopWallet w = ShopWallet.create(SHOP_ID);
        ReflectionTestUtils.setField(w, "balance", balance);
        return w;
    }

    @Test
    @DisplayName("정산 승인 성공 — ShopWallet 잔액 차감, APPROVED 전환")
    void approveSettlement_success() {
        Settlement settlement = createSettlement(SettlementStatus.PENDING);
        ShopWallet wallet = createWallet(AMOUNT);

        given(settlementRepository.findByIdWithLock(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
        given(shopWalletRepository.findByShopIdWithLock(SHOP_ID)).willReturn(Optional.of(wallet));

        adminSettlementService.approveSettlement(SETTLEMENT_ID);

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.APPROVED);
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("정산 없음 — SETTLEMENT_NOT_FOUND")
    void approveSettlement_notFound() {
        given(settlementRepository.findByIdWithLock(SETTLEMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminSettlementService.approveSettlement(SETTLEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 처리된 정산 승인 시도 — SETTLEMENT_INVALID_STATUS")
    void approveSettlement_alreadyApproved() {
        Settlement settlement = createSettlement(SettlementStatus.APPROVED);

        given(settlementRepository.findByIdWithLock(SETTLEMENT_ID)).willReturn(Optional.of(settlement));

        assertThatThrownBy(() -> adminSettlementService.approveSettlement(SETTLEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_STATUS);

        then(shopWalletRepository).should(never()).findByShopIdWithLock(any());
    }

    @Test
    @DisplayName("ShopWallet 잔액 부족 — INSUFFICIENT_BALANCE")
    void approveSettlement_insufficientWalletBalance() {
        Settlement settlement = createSettlement(SettlementStatus.PENDING);
        ShopWallet wallet = createWallet(AMOUNT - 1);

        given(settlementRepository.findByIdWithLock(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
        given(shopWalletRepository.findByShopIdWithLock(SHOP_ID)).willReturn(Optional.of(wallet));

        assertThatThrownBy(() -> adminSettlementService.approveSettlement(SETTLEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("정산 거절 성공 — REJECTED 전환, 사유 저장")
    void rejectSettlement_success() {
        Settlement settlement = createSettlement(SettlementStatus.PENDING);
        given(settlementRepository.findByIdWithLock(SETTLEMENT_ID)).willReturn(Optional.of(settlement));

        adminSettlementService.rejectSettlement(SETTLEMENT_ID, "정산 불가");

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.REJECTED);
        assertThat(settlement.getRejectReason()).isEqualTo("정산 불가");
    }

    @Test
    @DisplayName("정산 거절 사유 없음 — INVALID_INPUT_VALUE")
    void rejectSettlement_blankReason() {
        Settlement settlement = createSettlement(SettlementStatus.PENDING);
        given(settlementRepository.findByIdWithLock(SETTLEMENT_ID)).willReturn(Optional.of(settlement));

        assertThatThrownBy(() -> adminSettlementService.rejectSettlement(SETTLEMENT_ID, " "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("이미 처리된 정산 거절 시도 — SETTLEMENT_INVALID_STATUS")
    void rejectSettlement_alreadyRejected() {
        Settlement settlement = createSettlement(SettlementStatus.REJECTED);
        given(settlementRepository.findByIdWithLock(SETTLEMENT_ID)).willReturn(Optional.of(settlement));

        assertThatThrownBy(() -> adminSettlementService.rejectSettlement(SETTLEMENT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_STATUS);
    }

    @Test
    @DisplayName("관리자 정산 목록 조회 — 첫 페이지 (hasNext=false)")
    void getSettlements_firstPage() {
        Settlement s1 = createSettlement(SettlementStatus.PENDING);
        ReflectionTestUtils.setField(s1, "id", 2L);
        Settlement s2 = createSettlement(SettlementStatus.PENDING);
        ReflectionTestUtils.setField(s2, "id", 1L);

        given(settlementRepository.findAllWithCursor(isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of(s1, s2));

        CursorPageResponse<AdminSettlementResponse> result =
                adminSettlementService.getSettlements(null, 20, null);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("관리자 정산 목록 조회 — 다음 페이지 존재 (hasNext=true)")
    void getSettlements_hasNext() {
        Settlement s1 = createSettlement(SettlementStatus.PENDING);
        ReflectionTestUtils.setField(s1, "id", 3L);
        Settlement s2 = createSettlement(SettlementStatus.PENDING);
        ReflectionTestUtils.setField(s2, "id", 2L);
        Settlement s3 = createSettlement(SettlementStatus.PENDING);
        ReflectionTestUtils.setField(s3, "id", 1L);

        given(settlementRepository.findAllWithCursor(isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of(s1, s2, s3));

        CursorPageResponse<AdminSettlementResponse> result =
                adminSettlementService.getSettlements(null, 2, null);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("관리자 정산 목록 조회 — status=PENDING 필터")
    void getSettlements_filterByPending() {
        Settlement s1 = createSettlement(SettlementStatus.PENDING);
        ReflectionTestUtils.setField(s1, "id", 1L);

        given(settlementRepository.findAllWithCursor(isNull(), eq(SettlementStatus.PENDING), any(Pageable.class)))
                .willReturn(List.of(s1));

        CursorPageResponse<AdminSettlementResponse> result =
                adminSettlementService.getSettlements(null, 20, SettlementStatus.PENDING);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo(SettlementStatus.PENDING);
        assertThat(result.hasNext()).isFalse();
    }
}
