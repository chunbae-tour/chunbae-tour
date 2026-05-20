package com.chunbaetour.domain.chat.repository;

import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.type.JoinRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    // 개설자 승인/거부 목록 — status=PENDING 으로 호출
    List<JoinRequest> findByChatRoomIdAndStatus(Long chatRoomId, JoinRequestStatus status);

    // 신청 상세 조회·처리 — PENDING 상태로 한정해 NonUniqueResultException 방지
    Optional<JoinRequest> findByChatRoomIdAndUserIdAndStatus(Long chatRoomId, Long userId, JoinRequestStatus status);

    // 중복 신청 방지 — CHAT_004 선행 체크용
    boolean existsByChatRoomIdAndUserIdAndStatus(Long chatRoomId, Long userId, JoinRequestStatus status);
}
