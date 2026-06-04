package com.chunbaetour.domain.festival.repository;

import com.chunbaetour.domain.festival.entity.Festival;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalRepository extends JpaRepository<Festival, Long> {
}
