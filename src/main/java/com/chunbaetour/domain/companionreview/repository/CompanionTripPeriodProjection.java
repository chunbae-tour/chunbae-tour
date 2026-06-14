package com.chunbaetour.domain.companionreview.repository;

import java.time.LocalDate;

public interface CompanionTripPeriodProjection {
    Long getUserId();
    LocalDate getTripStartDate();
    LocalDate getTripEndDate();
}
