package com.chunbaetour.domain.support.repository;

import com.chunbaetour.domain.support.entity.SupportRoom;
import com.chunbaetour.domain.support.type.SupportRoomStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportRoomRepository extends JpaRepository<SupportRoom, Long> {

    // CS-5: USER 본인 상담방 목록 조회
    List<SupportRoom> findByUserIdOrderByIdDesc(Long userId);

    // CS-6: ADMIN 상담방 목록 — 상태별 필터
    List<SupportRoom> findByStatusOrderByIdDesc(SupportRoomStatus status);
}
