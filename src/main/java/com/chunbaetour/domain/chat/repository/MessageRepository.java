package com.chunbaetour.domain.chat.repository;

import com.chunbaetour.domain.chat.entity.Message;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // 메시지 커서 페이징 — id DESC(최신순), cursorId null 시 첫 페이지(상한 없음)
    @Query("SELECT m FROM Message m WHERE m.chatRoomId = :chatRoomId AND (:cursorId IS NULL OR m.id < :cursorId) ORDER BY m.id DESC")
    List<Message> findWithCursor(
            @Param("chatRoomId") Long chatRoomId,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
