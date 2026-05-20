package com.chunbaetour.domain.chat.repository;

import com.chunbaetour.domain.chat.entity.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // 채팅 내역 조회 — 시간 오름차순.
    // Message는 BaseEntity.createdAt만 가지고 별도 sentAt 필드를 두지 않으므로 createdAt 기준으로 정렬한다.
    // (원래 KAN-55 머지에 findByChatRoomIdOrderBySentAtAsc로 들어와 Spring Data가 property 매핑 실패로 컨텍스트 부팅 차단됨 → 본 S5 PR에서 함께 복구.)
    List<Message> findByChatRoomIdOrderByCreatedAtAsc(Long chatRoomId);
}
