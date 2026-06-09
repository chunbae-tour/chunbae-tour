package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.entity.PlaceSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceSyncStateRepository extends JpaRepository<PlaceSyncState, Long> {
}
