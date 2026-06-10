package com.chunbaetour.domain.shop.dto.response;

/**
 * 관리자 가게-장소 수동 연결/해제 결과 응답 DTO (KAN-254).
 *
 * <p>PATCH 후 변경 결과를 echo back 한다. 보정 API 특성상 관리자가 재조회 없이 "어떤 장소가 연결됐는지"
 * 즉시 확인할 수 있어야 하므로 연결된 장소 id·명칭과 연결 여부를 함께 반환한다.
 *
 * @param shopId    대상 가게 id
 * @param placeId   연결된 장소 id (해제 시 null)
 * @param placeName 연결된 장소명 (해제 시 null) — 관리자 UI 확인용
 * @param linked    연결 여부 (true=연결, false=해제)
 */
public record AdminShopPlaceResponse(
        Long shopId,
        Long placeId,
        String placeName,
        boolean linked
) {
    public static AdminShopPlaceResponse linked(Long shopId, Long placeId, String placeName) {
        return new AdminShopPlaceResponse(shopId, placeId, placeName, true);
    }

    public static AdminShopPlaceResponse unlinked(Long shopId) {
        return new AdminShopPlaceResponse(shopId, null, null, false);
    }
}
