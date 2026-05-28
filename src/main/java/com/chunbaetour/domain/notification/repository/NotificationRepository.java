package com.chunbaetour.domain.notification.repository;

import com.chunbaetour.domain.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 단건 읽음 처리용 — userId 일치 검증 포함 (타인 알림 접근 시 empty 반환)
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    // 멱등 삭제용 — soft-deleted 알림도 조회 (@SQLRestriction 우회, native query)
    @Query(value = "SELECT * FROM notifications WHERE id = :notificationId AND user_id = :userId", nativeQuery = true)
    Optional<Notification> findByIdAndUserIdIncludingDeleted(
            @Param("notificationId") Long notificationId,
            @Param("userId") Long userId);

    // 알림 목록 커서 페이징 — id DESC(최신순), cursorId null 시 첫 페이지(상한 없음)
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND (:cursorId IS NULL OR n.id < :cursorId) ORDER BY n.id DESC")
    List<Notification> findWithCursor(
            @Param("userId") Long userId,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // 전체 읽음 처리 — userId 기준 isRead=false 일괄 업데이트, 영향 행 수 반환
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false AND n.deletedAt IS NULL")
    int markAllAsRead(@Param("userId") Long userId);
}
