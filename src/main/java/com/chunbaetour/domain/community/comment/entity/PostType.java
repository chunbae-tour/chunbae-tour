package com.chunbaetour.domain.community.comment.entity;

public enum PostType {
    COMPANION, FREE;

    public static PostType from(String pathSegment) {
        return switch (pathSegment) {
            case "companions" -> COMPANION;
            case "free" -> FREE;
            default -> throw new IllegalArgumentException("Unknown postType: " + pathSegment);
        };
    }
}
