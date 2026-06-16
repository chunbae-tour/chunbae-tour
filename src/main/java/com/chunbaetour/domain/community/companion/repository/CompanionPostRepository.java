package com.chunbaetour.domain.community.companion.repository;

import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionPostRepository extends JpaRepository<CompanionPost, Long> {

    /** 자동 숨김 직렬화용 비관적 쓰기 락 — 동시 신고로 인한 임계값 경합 방지 (KAN-93). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CompanionPost p WHERE p.id = :id")
    Optional<CompanionPost> findByIdForUpdate(@Param("id") Long id);

    // 목록 cursor 조회는 동적 필터의 인덱스 사용을 보장하기 위해 CompanionPostQueryRepository(QueryDSL)로 분리.
}
