package com.chunbaetour.domain.festival.repository;

import com.chunbaetour.domain.festival.entity.Festival;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FestivalRepository extends JpaRepository<Festival, Long> {

    Optional<Festival> findByExternalId(String externalId);
}
