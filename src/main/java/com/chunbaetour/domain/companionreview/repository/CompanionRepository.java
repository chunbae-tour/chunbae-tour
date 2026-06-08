package com.chunbaetour.domain.companionreview.repository;

import com.chunbaetour.domain.companionreview.entity.Companion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanionRepository extends JpaRepository<Companion, Long> {

    // 채팅방당 동행 1번만 — 시작 전 중복 확인(CR_004) 및 진행 중 동행 조회에 사용
    Optional<Companion> findByChatRoomId(Long chatRoomId);
}
