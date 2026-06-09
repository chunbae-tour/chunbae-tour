package com.chunbaetour.domain.place.type;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 한국관광공사 TourAPI(KorService2) 지역코드(areacode) → 표준 시도명 매핑 (KAN-249).
 *
 * <p>관광지 수집 시 응답의 {@code areacode}로 시도(sido)를 정확히 결정한다(주소 표기 흔들림 무관).
 * 시군구(sigungu)는 sigungucode가 areacode별로 다른 방대한 코드체계라 매핑하지 않고
 * 주소 파싱({@code RegionParser})으로 처리한다.
 *
 * <p>시도명은 행정구역 개편 후 표준 표기(강원특별자치도/전북특별자치도/제주특별자치도/세종특별자치시)로 통일한다.
 */
public enum AreaCode {

    SEOUL("1", "서울특별시"),
    INCHEON("2", "인천광역시"),
    DAEJEON("3", "대전광역시"),
    DAEGU("4", "대구광역시"),
    GWANGJU("5", "광주광역시"),
    BUSAN("6", "부산광역시"),
    ULSAN("7", "울산광역시"),
    SEJONG("8", "세종특별자치시"),
    GYEONGGI("31", "경기도"),
    GANGWON("32", "강원특별자치도"),
    CHUNGBUK("33", "충청북도"),
    CHUNGNAM("34", "충청남도"),
    GYEONGBUK("35", "경상북도"),
    GYEONGNAM("36", "경상남도"),
    JEONBUK("37", "전북특별자치도"),
    JEONNAM("38", "전라남도"),
    JEJU("39", "제주특별자치도");

    private final String code;
    private final String sido;

    AreaCode(String code, String sido) {
        this.code = code;
        this.sido = sido;
    }

    private static final Map<String, AreaCode> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(a -> a.code, a -> a));

    /**
     * areacode → 표준 시도명. 미지정/미상 코드는 null 반환(호출부에서 주소 파싱 fallback).
     *
     * @param code TourAPI areacode 문자열(예: "1", "39")
     * @return 표준 시도명, 매칭 실패 시 null
     */
    public static String sidoOf(String code) {
        if (code == null) {
            return null;
        }
        AreaCode area = BY_CODE.get(code.trim());
        return area != null ? area.sido : null;
    }
}
