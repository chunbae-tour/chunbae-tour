package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.ShopWallet;

public record ShopAccountResponse(
        Long shopId,
        String bankName,
        String accountNumber,
        String accountHolder
) {
    public static ShopAccountResponse from(ShopWallet wallet) {
        return new ShopAccountResponse(
                wallet.getShopId(),
                wallet.getBankName(),
                maskAccountNumber(wallet.getAccountNumber()),
                wallet.getAccountHolder()
        );
    }

    /** 계좌번호 뒷 4자리 제외 마스킹 — 하이픈 유지. 4자 이하면 전부 * 처리 */
    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null) return null;
        if (accountNumber.length() <= 4) return "*".repeat(accountNumber.length());
        String visible = accountNumber.substring(accountNumber.length() - 4);
        String masked = accountNumber.substring(0, accountNumber.length() - 4)
                .replaceAll("[^\\-]", "*");
        return masked + visible;
    }
}
