package com.chunbaetour.domain.chat.repository;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 개설자 권한 확인용 — ownerId 불일치 시 CHAT_006
    Optional<ChatRoom> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByPostId(Long postId);
}
