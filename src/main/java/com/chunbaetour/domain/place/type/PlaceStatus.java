package com.chunbaetour.domain.place.type;

import java.util.List;

public enum PlaceStatus {
    ACTIVE,         // 정상 노출
    HIDDEN,         // 관리자 숨김
    DELETED,        // 운영자(관리자) 수동 삭제 — 원천 재노출돼도 부활 안 함(운영 의사 존중)
    SOURCE_DELETED; // 원천(TourAPI) 삭제(showflag != "1") — 원천 재노출(showflag=1) 시 ACTIVE로 부활 (KAN-306)

    /**
     * 노출·연결 가능 상태 화이트리스트 (KAN-306).
     * 운영자 목록·가게 연결 검증 등 "삭제계열(DELETED/SOURCE_DELETED) 제외" 판정의 단일 기준.
     * 신규 비노출 상태 추가 시 != 가드(블랙리스트)는 새 상태를 통과시키는 회귀가 생기므로 화이트리스트로 통일한다.
     */
    public static List<PlaceStatus> visibleStatuses() {
        return List.of(ACTIVE, HIDDEN);
    }
}
