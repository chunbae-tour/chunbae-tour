package com.chunbaetour.domain.cs.repository;

import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportRoomRepository extends JpaRepository<SupportRoom, Long> {

    // CS-5: 활성 상담방(WAITING/IN_PROGRESS) 존재 여부 — 중복 생성 차단용
    boolean existsByUserIdAndStatusIn(Long userId, List<SupportRoomStatus> statuses);

    // CS-6: USER 본인 상담방 cursor 페이징 — status 필터 선택
    @Query("SELECT r FROM SupportRoom r WHERE r.userId = :userId AND (:status IS NULL OR r.status = :status) AND (:cursorId IS NULL OR r.id < :cursorId) ORDER BY r.id DESC")
    List<SupportRoom> findMyRoomsWithCursor(@Param("userId") Long userId, @Param("status") SupportRoomStatus status, @Param("cursorId") Long cursorId, Pageable pageable);

    // CS-6: ADMIN 상담방 cursor 페이징 — status 필터 선택
    @Query("SELECT r FROM SupportRoom r WHERE (:status IS NULL OR r.status = :status) AND (:cursorId IS NULL OR r.id < :cursorId) ORDER BY r.id DESC")
    List<SupportRoom> findAllRoomsWithCursor(@Param("status") SupportRoomStatus status, @Param("cursorId") Long cursorId, Pageable pageable);

    // CS-8: ADMIN 배정 — WAITING 상태일 때만 원자적 UPDATE. 동시 배정 경합 방어
    // clearAutomatically = true: UPDATE 후 JPA 1차 캐시 무효화 → 이후 findById 시 최신 상태 반환
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SupportRoom r SET r.adminId = :adminId, r.status = :nextStatus WHERE r.id = :supportRoomId AND r.status = :currentStatus")
    int assignIfWaiting(@Param("supportRoomId") Long supportRoomId, @Param("adminId") Long adminId,
            @Param("nextStatus") SupportRoomStatus nextStatus, @Param("currentStatus") SupportRoomStatus currentStatus);
}
