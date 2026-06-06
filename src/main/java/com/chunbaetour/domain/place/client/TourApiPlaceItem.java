package com.chunbaetour.domain.place.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 한국관광공사 KorService2 areaBasedList2(지역기반 관광정보) 단일 아이템 — contentTypeId=12(관광지) 기준.
 *
 * <p>Tier-1 목록 수집에 필요한 필드만 매핑한다. 상세 필드(overview/usetime 등)는 Tier-2에서 detailCommon2·
 * detailIntro2로 별도 수집한다.
 *
 * <p>좌표: {@code mapx}=경도(longitude), {@code mapy}=위도(latitude) (WGS84).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiPlaceItem(
        @JsonProperty("contentid")   String contentId,
        @JsonProperty("title")       String title,
        @JsonProperty("addr1")       String addr1,
        @JsonProperty("addr2")       String addr2,
        @JsonProperty("mapx")        String mapX,
        @JsonProperty("mapy")        String mapY,
        @JsonProperty("firstimage")  String firstImage,
        @JsonProperty("firstimage2") String firstImage2,
        @JsonProperty("tel")         String tel,
        @JsonProperty("modifiedtime") String modifiedTime,
        // areaBasedSyncList2 전용: "1"=노출, 그 외=삭제/비노출. areaBasedList2 응답엔 없어 null.
        @JsonProperty("showflag")    String showFlag
) {
    /** addr1 + addr2 합친 전체 주소(공백 정리). addr1 없으면 빈 문자열. */
    public String fullAddress() {
        String base = addr1 == null ? "" : addr1.trim();
        if (addr2 != null && !addr2.isBlank()) {
            base = (base + " " + addr2.trim()).trim();
        }
        return base;
    }

    /** 동기화 응답에서 삭제(비노출) 처리된 항목인지. showflag가 "1"이 아니면 삭제로 본다(null은 노출로 간주). */
    public boolean isDeleted() {
        return showFlag != null && !"1".equals(showFlag);
    }
}
