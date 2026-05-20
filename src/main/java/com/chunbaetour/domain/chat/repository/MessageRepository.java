package com.chunbaetour.domain.chat.repository;

import com.chunbaetour.domain.chat.entity.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // 채팅 내역 조회 — sentAt 오름차순
    List<Message> findByChatRoomIdOrderBySentAtAsc(Long chatRoomId);
}
