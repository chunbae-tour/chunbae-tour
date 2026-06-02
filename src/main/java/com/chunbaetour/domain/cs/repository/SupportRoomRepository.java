package com.chunbaetour.domain.cs.repository;

import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportRoomRepository extends JpaRepository<SupportRoom, Long> {

    // CS-5: 활성 상담방(WAITING/IN_PROGRESS) 존재 여부 — 중복 생성 차단용
    boolean existsByUserIdAndStatusIn(Long userId, List<SupportRoomStatus> statuses);

    // CS-5: USER 본인 상담방 목록 조회
    List<SupportRoom> findByUserIdOrderByIdDesc(Long userId);

    // CS-6: ADMIN 상담방 목록 — 상태별 필터
    List<SupportRoom> findByStatusOrderByIdDesc(SupportRoomStatus status);
}
