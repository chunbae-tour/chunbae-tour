package com.chunbaetour.domain.place.client;

/**
 * KorService2 상세 수집 결과(Tier-2). detailCommon2 + detailIntro2를 합쳐 Place에 채울 값만 담는다.
 *
 * @param description    detailCommon2 overview (관광지 개요)
 * @param operatingHours detailIntro2 usetime (이용시간)
 * @param closedDays     detailIntro2 restdate (쉬는날)
 */
public record TourApiPlaceDetail(
        String description,
        String operatingHours,
        String closedDays
) {}
