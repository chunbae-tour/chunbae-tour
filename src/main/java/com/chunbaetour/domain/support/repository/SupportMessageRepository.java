package com.chunbaetour.domain.support.repository;

import com.chunbaetour.domain.support.entity.SupportMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    // CS-6: 상담방 메시지 목록 — 시간순 정렬
    List<SupportMessage> findBySupportRoomIdOrderBySentAtAsc(Long supportRoomId);
}
