package com.chunbaetour.domain.common.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 도로명/지번 주소 문자열에서 행정구역(시도/시군구)을 추출하는 공통 파서 (KAN-249).
 *
 * <p>관광지(place)·전통시장(market) 등 외부 API로 수집한 주소에서 지역 기반 검색용
 * 정규화 값을 뽑는다. 결과 포맷을 도메인 간 통일해(예: sido="서울특별시", sigungu="수원시")
 * 검색 측이 같은 기준으로 필터할 수 있게 한다.
 *
 * <p>정책:
 * <ul>
 *   <li>sido: 표준 시도 풀네임으로 정규화. 개편 전 구표기(강원도/전라북도/제주도)도 흡수.</li>
 *   <li>sigungu: 시도 제거 후 첫 행정단위(시/군/구). "수원시 팔달구"는 시 단위("수원시")로 통일.</li>
 *   <li>매칭 실패 시 해당 값 null — 호출부에서 null 가드.</li>
 * </ul>
 */
public final class RegionParser {

    private RegionParser() {
    }

    /** 파싱 결과. 추출 실패한 항목은 null. */
    public record Region(String sido, String sigungu) {
        public static final Region EMPTY = new Region(null, null);
    }

    /**
     * 주소 접두사 별칭 → 표준 시도명.
     * 긴 별칭이 먼저 매칭되도록 LinkedHashMap에 길이 내림차순으로 넣는다
     * (예: "서울특별시"가 "서울"보다 우선). 공공데이터 주소는 시도 풀네임으로 시작하므로
     * 풀네임 + 개편 전 구표기만 등록한다.
     */
    private static final Map<String, String> SIDO_ALIASES = buildAliases();

    private static Map<String, String> buildAliases() {
        Map<String, String> m = new LinkedHashMap<>();
        // 광역/특별시
        m.put("서울특별시", "서울특별시");
        m.put("부산광역시", "부산광역시");
        m.put("대구광역시", "대구광역시");
        m.put("인천광역시", "인천광역시");
        m.put("광주광역시", "광주광역시");
        m.put("대전광역시", "대전광역시");
        m.put("울산광역시", "울산광역시");
        m.put("세종특별자치시", "세종특별자치시");
        // 도 — 개편 후 표준
        m.put("경기도", "경기도");
        m.put("강원특별자치도", "강원특별자치도");
        m.put("충청북도", "충청북도");
        m.put("충청남도", "충청남도");
        m.put("전북특별자치도", "전북특별자치도");
        m.put("전라남도", "전라남도");
        m.put("경상북도", "경상북도");
        m.put("경상남도", "경상남도");
        m.put("제주특별자치도", "제주특별자치도");
        // 개편 전 구표기 흡수 (수집 데이터 시점에 따라 존재 가능)
        m.put("강원도", "강원특별자치도");
        m.put("전라북도", "전북특별자치도");
        m.put("제주도", "제주특별자치도");
        m.put("세종시", "세종특별자치시");
        return m;
    }

    /**
     * 주소에서 (sido, sigungu)를 추출한다.
     *
     * @param address 도로명/지번 주소. null/blank면 {@link Region#EMPTY}.
     * @return 정규화된 지역. 추출 실패 항목은 null.
     */
    public static Region parse(String address) {
        if (address == null || address.isBlank()) {
            return Region.EMPTY;
        }
        String addr = address.strip();

        // 시도: 등록된 별칭 중 주소가 그 별칭으로 시작하는 것을 찾는다(긴 별칭 우선 순회).
        for (Map.Entry<String, String> entry : SIDO_ALIASES.entrySet()) {
            String alias = entry.getKey();
            if (addr.startsWith(alias)) {
                String sido = entry.getValue();
                String rest = addr.substring(alias.length()).strip();
                return new Region(sido, firstAdminUnit(rest));
            }
        }
        return Region.EMPTY;
    }

    /**
     * 시도 제거 후 남은 주소에서 첫 행정단위(시/군/구)를 추출한다.
     * "수원시 팔달구 ..." → "수원시"(첫 토큰=시 단위). "중구 ..." → "중구".
     * 첫 토큰이 시/군/구로 끝나지 않으면(세종시 하위 동 등) null.
     */
    private static String firstAdminUnit(String rest) {
        if (rest.isBlank()) {
            return null;
        }
        String first = rest.split("\\s+", 2)[0];
        if (first.endsWith("시") || first.endsWith("군") || first.endsWith("구")) {
            return first;
        }
        return null;
    }
}
