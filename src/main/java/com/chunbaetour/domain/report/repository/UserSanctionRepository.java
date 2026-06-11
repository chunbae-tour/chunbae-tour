package com.chunbaetour.domain.report.repository;

import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.UserSanction;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSanctionRepository extends JpaRepository<UserSanction, Long> {

    /** 유저 전체 제재 이력 — 관리자 조회용. 최신순. */
    List<UserSanction> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 유저·도메인별 전체 제재 이력 — 최고 단계 비교용. */
    List<UserSanction> findByUserIdAndTargetType(Long userId, ReportTargetType targetType);

    /**
     * 활성 제재가 존재하는 도메인 수 — 2개 이상이면 계정 정지 트리거.
     * WARNING 제외: ended_at = started_at 이므로 자동 제외됨.
     */
    @Query("SELECT COUNT(DISTINCT s.targetType) FROM UserSanction s "
            + "WHERE s.userId = :userId "
            + "AND s.releasedAt IS NULL "
            + "AND (s.endedAt IS NULL OR s.endedAt > :now) "
            + "AND s.targetType != com.chunbaetour.domain.report.entity.ReportTargetType.MERCHANT")
    long countActiveDistinctDomainsByUserId(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now);

    /**
     * 유저의 모든 활성 제재 — 크로스 도메인 계정 정지 시 최고 단계·최대 endedAt 계산용.
     * WARNING 제외: ended_at = started_at 이므로 자동 제외됨.
     */
    @Query("SELECT s FROM UserSanction s "
            + "WHERE s.userId = :userId "
            + "AND s.releasedAt IS NULL "
            + "AND (s.endedAt IS NULL OR s.endedAt > :now) "
            + "AND s.targetType != com.chunbaetour.domain.report.entity.ReportTargetType.MERCHANT")
    List<UserSanction> findAllActiveSanctionsByUserId(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now);

    /**
     * 유저·도메인별 활성 제재 — 인터셉터 도메인별 쓰기 차단 판정용.
     * WARNING 제외: ended_at = started_at 이므로 자동 제외됨 (경고는 차단하지 않음).
     */
    @Query("SELECT s FROM UserSanction s "
            + "WHERE s.userId = :userId "
            + "AND s.targetType = :targetType "
            + "AND s.releasedAt IS NULL "
            + "AND (s.endedAt IS NULL OR s.endedAt > :now)")
    List<UserSanction> findActiveSanctionsByUserIdAndTargetType(
            @Param("userId") Long userId,
            @Param("targetType") ReportTargetType targetType,
            @Param("now") LocalDateTime now);

    /**
     * 만료됐지만 아직 release 처리되지 않은 제재 — 스케줄러 일괄 해제용.
     * PERMANENT(ended_at IS NULL)는 제외.
     * WARNING 제재는 ended_at = started_at이므로 생성 직후 이 쿼리에 포함되어 즉시 release 처리됨.
     */
    @Query("SELECT s FROM UserSanction s "
            + "WHERE s.releasedAt IS NULL "
            + "AND s.endedAt IS NOT NULL "
            + "AND s.endedAt <= :now")
    List<UserSanction> findExpiredUnreleasedSanctions(@Param("now") LocalDateTime now);
}
