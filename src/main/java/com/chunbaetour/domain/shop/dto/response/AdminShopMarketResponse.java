package com.chunbaetour.domain.shop.dto.response;

/**
 * 관리자 가게-전통시장 수동 연결/해제 결과 응답 DTO (KAN-268).
 *
 * <p>PATCH 후 변경 결과를 echo back 한다. 보정 API 특성상 관리자가 재조회 없이 "어떤 전통시장이 연결됐는지"
 * 즉시 확인할 수 있어야 하므로 연결된 시장 id·명칭과 연결 여부를 함께 반환한다. (가게-장소 연결 KAN-254와 동일 패턴)
 *
 * @param shopId                대상 가게 id
 * @param traditionalMarketId   연결된 전통시장 id (해제 시 null)
 * @param marketName            연결된 전통시장명 (해제 시 null) — 관리자 UI 확인용
 * @param linked                연결 여부 (true=연결, false=해제)
 */
public record AdminShopMarketResponse(
        Long shopId,
        Long traditionalMarketId,
        String marketName,
        boolean linked
) {
    public static AdminShopMarketResponse linked(Long shopId, Long traditionalMarketId, String marketName) {
        return new AdminShopMarketResponse(shopId, traditionalMarketId, marketName, true);
    }

    public static AdminShopMarketResponse unlinked(Long shopId) {
        return new AdminShopMarketResponse(shopId, null, null, false);
    }
}
