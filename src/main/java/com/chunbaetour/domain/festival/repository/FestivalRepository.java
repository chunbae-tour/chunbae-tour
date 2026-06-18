package com.chunbaetour.domain.festival.repository;

import com.chunbaetour.domain.festival.entity.Festival;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import com.chunbaetour.domain.festival.type.FestivalStatus;

public interface FestivalRepository extends JpaRepository<Festival, Long> {

    Optional<Festival> findByExternalId(String externalId);

    Optional<Festival> findByIdAndStatus(Long id, FestivalStatus status);

    /**
     * 오타 교정 사전 로드용 — 전체 축제 제목 조회 (KAN-276).
     *
     * <p>TypoCorrectionService가 서버 시작 시 Redis 2-gram 역인덱스를 구성하기 위해 사용한다.
     */
    @Query("SELECT f.name FROM Festival f")
    List<String> findAllNames();
}
