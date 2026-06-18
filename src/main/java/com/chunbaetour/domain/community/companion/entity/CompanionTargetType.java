package com.chunbaetour.domain.community.companion.entity;

/**
 * 동행 게시글의 만남 대상 종류 (KAN-322).
 *
 * <p>기존엔 장소(Place)만 대상이라 placeId만 받았으나, 전통시장·축제도 동행 대상으로 선택할 수 있도록
 * 다형 참조(targetType + targetId)로 확장한다. 프론트는 targetType에 따라 해당 상세 API를 호출해
 * 좌표(latitude/longitude)를 받아 지도에 표시한다.
 */
public enum CompanionTargetType {
    PLACE, MARKET, FESTIVAL
}
