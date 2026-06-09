package com.chunbaetour.domain.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.common.util.RegionParser.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionParserTest {

    @Test
    @DisplayName("광역시 주소 → 시도/시군구(구) 추출")
    void parseMetropolitan() {
        Region region = RegionParser.parse("서울특별시 중구 을지로 203");

        assertThat(region.sido()).isEqualTo("서울특별시");
        assertThat(region.sigungu()).isEqualTo("중구");
    }

    @Test
    @DisplayName("도 주소 → 시 단위까지만 추출 ('수원시 팔달구'는 '수원시')")
    void parseProvinceCity() {
        Region region = RegionParser.parse("경기도 수원시 팔달구 효원로 1");

        assertThat(region.sido()).isEqualTo("경기도");
        assertThat(region.sigungu()).isEqualTo("수원시");
    }

    @Test
    @DisplayName("개편 전 구표기(강원도)는 표준 표기(강원특별자치도)로 정규화")
    void normalizeGangwon() {
        Region region = RegionParser.parse("강원도 춘천시 중앙로 1");

        assertThat(region.sido()).isEqualTo("강원특별자치도");
        assertThat(region.sigungu()).isEqualTo("춘천시");
    }

    @Test
    @DisplayName("개편 전 구표기(전라북도)는 표준 표기(전북특별자치도)로 정규화")
    void normalizeJeonbuk() {
        Region region = RegionParser.parse("전라북도 전주시 완산구 효자로 225");

        assertThat(region.sido()).isEqualTo("전북특별자치도");
        assertThat(region.sigungu()).isEqualTo("전주시");
    }

    @Test
    @DisplayName("세종특별자치시는 시군구가 없어 sigungu는 null")
    void parseSejong() {
        Region region = RegionParser.parse("세종특별자치시 한누리대로 2130");

        assertThat(region.sido()).isEqualTo("세종특별자치시");
        assertThat(region.sigungu()).isNull();
    }

    @Test
    @DisplayName("군 단위 주소 추출")
    void parseGun() {
        Region region = RegionParser.parse("경상남도 거창군 거창읍");

        assertThat(region.sido()).isEqualTo("경상남도");
        assertThat(region.sigungu()).isEqualTo("거창군");
    }

    @Test
    @DisplayName("null/blank 주소는 EMPTY")
    void parseBlank() {
        assertThat(RegionParser.parse(null)).isEqualTo(Region.EMPTY);
        assertThat(RegionParser.parse("   ")).isEqualTo(Region.EMPTY);
    }

    @Test
    @DisplayName("표준 시도로 시작하지 않는 주소는 EMPTY")
    void parseUnknown() {
        Region region = RegionParser.parse("미국 뉴욕 5번가");

        assertThat(region).isEqualTo(Region.EMPTY);
    }
}
