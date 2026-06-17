package com.chunbaetour.domain.report.repository;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // ── 중복 신고 체크 ────────────────────────────────────────────────────
    boolean existsByReporterIdAndTargetTypeAndTargetId(
            Long reporterId, ReportTargetType targetType, Long targetId);

    // ── 내 신고 내역 cursor 페이징 (KAN-90) ──────────────────────────────
    List<Report> findByReporterIdOrderByIdDesc(Long reporterId, Pageable pageable);

    List<Report> findByReporterIdAndIdLessThanOrderByIdDesc(
            Long reporterId, Long cursorId, Pageable pageable);

    // ── 관리자 목록 조회 cursor 페이징 (KAN-91) ──────────────────────────
    @Query("SELECT r FROM Report r ORDER BY r.id DESC")
    List<Report> findAllOrderByIdDesc(Pageable pageable);

    @Query("SELECT r FROM Report r WHERE r.id < :cursorId ORDER BY r.id DESC")
    List<Report> findByIdLessThanOrderByIdDesc(@Param("cursorId") Long cursorId, Pageable pageable);

    @Query("SELECT r FROM Report r WHERE r.status = :status ORDER BY r.id DESC")
    List<Report> findByStatusOrderByIdDesc(@Param("status") ReportStatus status, Pageable pageable);

    @Query("SELECT r FROM Report r WHERE r.status = :status AND r.id < :cursorId ORDER BY r.id DESC")
    List<Report> findByStatusAndIdLessThanOrderByIdDesc(
            @Param("status") ReportStatus status,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // ── 미처리 신고 건수 (PR6 pending-count badge) ────────────────────
    long countByStatus(ReportStatus status);

    // ── 자동 숨김용 신고 건수 집계 (KAN-93) ─────────────────────────────
    long countByTargetTypeAndTargetIdAndStatus(
            ReportTargetType targetType, Long targetId, ReportStatus status);

    // ── 관리자 사용자 상세 누적 신고 건수 (KAN-180 Admin S02) ────────────
    // 상태 무관 전체 누적 — 운영자가 대상의 신고 이력 규모를 한눈에 파악.
    long countByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId);

    // ── 도메인별 제재 누적 집계 (PR1+PR2 SanctionService) ──────────────────
    long countByReportedUserIdAndTargetTypeAndStatus(
            Long reportedUserId, ReportTargetType targetType, ReportStatus status);

    // ── 1년 롤링 윈도우 누적 집계 (제재 단계 판정) ──────────────────────────
    // resolved_at >= since(=now-1년) 인 RESOLVED 신고만 집계. 오래된 신고 자동 만료.
    long countByReportedUserIdAndTargetTypeAndStatusAndResolvedAtGreaterThanEqual(
            Long reportedUserId, ReportTargetType targetType, ReportStatus status, LocalDateTime since);
}
