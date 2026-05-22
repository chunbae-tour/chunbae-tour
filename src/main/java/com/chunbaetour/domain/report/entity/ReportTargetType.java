package com.chunbaetour.domain.report.entity;

public enum ReportTargetType {
    POST_COMPANION, // companion_posts 테이블
    POST_FREE,      // free_posts 테이블
    COMMENT,        // comments 테이블 — KAN-61 merge 후 존재 검증 연결 예정
    USER,           // users 테이블 (Role.USER)
    MERCHANT        // users 테이블 (Role.MERCHANT) — 별도 테이블 없이 Account.role로 구분
}
