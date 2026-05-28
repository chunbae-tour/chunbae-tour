package com.chunbaetour.domain.report.entity;

public enum ReportStatus {
    PENDING,   // 접수됨, 관리자 처리 대기 중
    RESOLVED,  // 신고 인정 — 관리자가 제재 조치 처리 완료
    DISMISSED  // 신고 기각 — 관리자가 문제없음 판단
}
