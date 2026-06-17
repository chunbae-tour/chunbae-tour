package com.chunbaetour.domain.place;

public enum PlaceReviewStatus {
    ACTIVE,   // 활성 상태
    HIDDEN,   // 행정 숨김(신고 처리·자동제재) — 복원 가능
    DELETED   // 작성자 자발 삭제 (soft delete) — 복원 대상 아님
}
