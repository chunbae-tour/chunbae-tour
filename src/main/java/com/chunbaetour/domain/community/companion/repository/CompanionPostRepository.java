package com.chunbaetour.domain.community.companion.repository;

import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionPostRepository extends JpaRepository<CompanionPost, Long> {

    /** 자동 숨김 직렬화용 비관적 쓰기 락 — 동시 신고로 인한 임계값 경합 방지 (KAN-93). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CompanionPost p WHERE p.id = :id")
    Optional<CompanionPost> findByIdForUpdate(@Param("id") Long id);

    // 목록 cursor 조회는 동적 필터의 인덱스 사용을 보장하기 위해 CompanionPostQueryRepository(QueryDSL)로 분리.

    // 영구 정지 유저의 ACTIVE 동행글 일괄 행정 숨김(HIDDEN). 벌크 UPDATE는 영속성 컨텍스트·Auditing을
    // 우회하므로 flush(정지 dirty Account 보존)·clear·updatedAt을 명시.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CompanionPost p SET p.status = com.chunbaetour.domain.community.companion.entity.CompanionPostStatus.HIDDEN, p.updatedAt = :now WHERE p.authorId = :authorId AND p.status = com.chunbaetour.domain.community.companion.entity.CompanionPostStatus.ACTIVE")
    void hideAllActiveByAuthorId(@Param("authorId") Long authorId, @Param("now") LocalDateTime now);

    /**
     * 조회수 원자적 +1. 벌크 UPDATE라 엔티티 dirty checking을 거치지 않아
     * {@code @LastModifiedDate updatedAt}을 건드리지 않고(조회=수정 오염 방지),
     * 동시 조회 시 lost-update도 발생하지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CompanionPost p SET p.viewCount = p.viewCount + 1 "
            + "WHERE p.id = :id AND p.status = com.chunbaetour.domain.community.companion.entity.CompanionPostStatus.ACTIVE")
    void incrementViewCount(@Param("id") Long id);
}
