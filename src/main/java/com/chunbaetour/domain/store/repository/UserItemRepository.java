package com.chunbaetour.domain.store.repository;

import com.chunbaetour.domain.store.entity.UserItem;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    /** 내 보유 아이템 — cursor keyset 페이징 (id DESC) */
    @Query("""
            SELECT i FROM UserItem i
            WHERE i.userId = :userId
            AND (:cursorId IS NULL OR i.id < :cursorId)
            ORDER BY i.id DESC
            """)
    List<UserItem> findItemsByUserIdWithCursor(
            @Param("userId") Long userId,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
