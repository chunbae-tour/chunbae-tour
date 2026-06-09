package com.chunbaetour.domain.place.type;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AreaCodeTest {

    @Test
    @DisplayName("areacode → 표준 시도명 매핑")
    void sidoOf() {
        assertThat(AreaCode.sidoOf("1")).isEqualTo("서울특별시");
        assertThat(AreaCode.sidoOf("6")).isEqualTo("부산광역시");
        assertThat(AreaCode.sidoOf("31")).isEqualTo("경기도");
        assertThat(AreaCode.sidoOf("39")).isEqualTo("제주특별자치도");
    }

    @Test
    @DisplayName("개편된 행정구역은 표준 표기로 매핑")
    void sidoOfRenamed() {
        assertThat(AreaCode.sidoOf("32")).isEqualTo("강원특별자치도");
        assertThat(AreaCode.sidoOf("37")).isEqualTo("전북특별자치도");
        assertThat(AreaCode.sidoOf("8")).isEqualTo("세종특별자치시");
    }

    @Test
    @DisplayName("앞뒤 공백은 trim 처리")
    void sidoOfTrim() {
        assertThat(AreaCode.sidoOf(" 1 ")).isEqualTo("서울특별시");
    }

    @Test
    @DisplayName("null/미상 코드는 null (호출부 주소 파싱 fallback)")
    void sidoOfUnknown() {
        assertThat(AreaCode.sidoOf(null)).isNull();
        assertThat(AreaCode.sidoOf("999")).isNull();
        assertThat(AreaCode.sidoOf("")).isNull();
    }
}
