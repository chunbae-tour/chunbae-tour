package com.chunbaetour.domain.chat.repository;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 개설자 권한 확인용 — ownerId 불일치 시 CHAT_006
    Optional<ChatRoom> findByIdAndOwnerId(Long id, Long ownerId);

    // 참여 수락 시 currentMembers 변경 전 배타적 잠금 — Redis 장애 시 DB 단독으로 정원 정합성 보장
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChatRoom c WHERE c.id = :id")
    Optional<ChatRoom> findByIdWithLock(@Param("id") Long id);
}
