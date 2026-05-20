package com.chunbaetour.domain.yeopjeon.repository;

import com.chunbaetour.domain.yeopjeon.entity.YeopjeonHistory;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface YeopjeonHistoryRepository extends JpaRepository<YeopjeonHistory, Long> {

    @Query("SELECT h FROM YeopjeonHistory h WHERE h.userId = :userId AND h.id < :cursorId ORDER BY h.id DESC")
    List<YeopjeonHistory> findByUserIdAndIdLessThanOrderByIdDesc(
            @Param("userId") Long userId,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("SELECT h FROM YeopjeonHistory h WHERE h.userId = :userId ORDER BY h.id DESC")
    List<YeopjeonHistory> findByUserIdOrderByIdDesc(
            @Param("userId") Long userId,
            Pageable pageable
    );
}
