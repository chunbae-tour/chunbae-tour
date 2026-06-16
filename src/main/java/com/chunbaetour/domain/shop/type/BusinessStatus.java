package com.chunbaetour.domain.shop.type;

/**
 * 가게 실시간 영업여부 (B7 MVP, KAN-301).
 * operatingHours + 현재시각(KST)으로 조회 시점에 산출하는 파생값 — DB에 저장하지 않는다.
 */
public enum BusinessStatus {
    OPEN,     // 영업중
    CLOSED,   // 영업종료
    UNKNOWN   // 운영시간 정보 없음/파싱 불가
}
