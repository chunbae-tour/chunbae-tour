package com.chunbaetour.domain.cs.repository;

import com.chunbaetour.domain.cs.entity.SupportMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    // CS-6: 메시지 cursor 페이징 — sentAt ASC (오래된 메시지부터)
    @Query("SELECT m FROM SupportMessage m WHERE m.supportRoomId = :roomId AND (:cursorId IS NULL OR m.id > :cursorId) ORDER BY m.id ASC")
    List<SupportMessage> findMessagesWithCursor(@Param("roomId") Long roomId, @Param("cursorId") Long cursorId, Pageable pageable);

    // CS-6: Admin 목록용 마지막 메시지 조회
    Optional<SupportMessage> findTopBySupportRoomIdOrderBySentAtDesc(Long supportRoomId);
}
