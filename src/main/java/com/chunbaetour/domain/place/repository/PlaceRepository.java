package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.Place;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
}
