package com.chunbaetour.domain.place.type;

public enum PlaceStatus {
    ACTIVE,         // 정상 노출
    HIDDEN,         // 관리자 숨김
    DELETED,        // 운영자(관리자) 수동 삭제 — 원천 재노출돼도 부활 안 함(운영 의사 존중)
    SOURCE_DELETED  // 원천(TourAPI) 삭제(showflag != "1") — 원천 재노출(showflag=1) 시 ACTIVE로 부활 (KAN-306)
}
