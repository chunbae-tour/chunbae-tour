package com.chunbaetour.domain.chat.repository;

import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.type.JoinRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    // 개설자 승인/거부 목록 — status=PENDING, 신청 순서(ASC) 정렬
    List<JoinRequest> findByChatRoomIdAndStatusOrderByCreatedAtAsc(Long chatRoomId, JoinRequestStatus status);

    // 신청 상세 조회·처리 — PENDING 상태로 한정해 NonUniqueResultException 방지
    Optional<JoinRequest> findByChatRoomIdAndUserIdAndStatus(Long chatRoomId, Long userId, JoinRequestStatus status);

    // 중복 신청 방지 — CHAT_004 선행 체크용
    boolean existsByChatRoomIdAndUserIdAndStatus(Long chatRoomId, Long userId, JoinRequestStatus status);

    // approve/reject 경합 직렬화 — SELECT FOR UPDATE로 행 잠금, 최신 status 재확인
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM JoinRequest j WHERE j.id = :id")
    Optional<JoinRequest> findByIdWithLock(@Param("id") Long id);

    // 조건부 원자적 거절 — WHERE status=PENDING으로 이중 거절 차단, 영향 행 수 반환 (0이면 이미 처리된 신청)
    @Transactional
    @Modifying
    @Query("UPDATE JoinRequest j SET j.status = com.chunbaetour.domain.chat.type.JoinRequestStatus.REJECTED, j.pendingKey = null WHERE j.id = :id AND j.status = com.chunbaetour.domain.chat.type.JoinRequestStatus.PENDING")
    int rejectIfPending(@Param("id") Long id);

    // 조건부 원자적 취소 — WHERE status=PENDING으로 approve 경합 시 이중 처리 차단, 영향 행 수 반환 (0이면 이미 처리된 신청)
    @Modifying
    @Query("DELETE FROM JoinRequest j WHERE j.id = :id AND j.status = com.chunbaetour.domain.chat.type.JoinRequestStatus.PENDING")
    int deleteIfPending(@Param("id") Long id);
}
