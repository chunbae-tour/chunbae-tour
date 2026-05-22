package com.chunbaetour.domain.community.comment.entity;

public enum PostType {
    COMPANION, FREE;

    // URL 경로 세그먼트는 REST 복수형 관례(companions, free)를 따르고
    // enum 이름은 단수형(COMPANION, FREE)이라 직접 valueOf() 사용 불가 — 명시적 매핑
    public static PostType from(String pathSegment) {
        return switch (pathSegment) {
            case "companions" -> COMPANION;
            case "free" -> FREE;
            default -> throw new IllegalArgumentException("Unknown postType: " + pathSegment);
        };
    }
}
