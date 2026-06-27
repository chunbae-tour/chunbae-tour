package com.chunbaetour.domain.companion.repository;

import java.time.LocalDate;

public interface CompanionTripPeriodProjection {
    Long getUserId();
    LocalDate getTripStartDate();
    LocalDate getTripEndDate();
}
