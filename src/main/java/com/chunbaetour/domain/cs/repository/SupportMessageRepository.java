package com.chunbaetour.domain.cs.repository;

import com.chunbaetour.domain.cs.entity.SupportMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    // CS-6: 메시지 cursor 페이징 — id DESC (최신 먼저, 위 스크롤로 이전 메시지 로드)
    @Query("SELECT m FROM SupportMessage m WHERE m.supportRoomId = :roomId AND (:cursorId IS NULL OR m.id < :cursorId) ORDER BY m.id DESC")
    List<SupportMessage> findMessagesWithCursor(@Param("roomId") Long roomId, @Param("cursorId") Long cursorId, Pageable pageable);

    // CS-6: Admin 목록 lastMessage 배치 조회 — 방 N개를 쿼리 1번에 처리
    @Query("SELECT m FROM SupportMessage m WHERE m.id IN (SELECT MAX(m2.id) FROM SupportMessage m2 WHERE m2.supportRoomId IN :roomIds GROUP BY m2.supportRoomId)")
    List<SupportMessage> findLastMessagesByRoomIds(@Param("roomIds") List<Long> roomIds);
}
