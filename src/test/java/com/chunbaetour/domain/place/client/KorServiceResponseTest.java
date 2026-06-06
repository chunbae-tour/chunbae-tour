package com.chunbaetour.domain.place.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * KorService2 응답 역직렬화 단위 테스트.
 * 축제(표준데이터)와 구조가 달라(중첩 items.item, 숫자형 카운트, 성공코드 "0000") 별도 검증한다.
 */
class KorServiceResponseTest {

    // RestClient 빈(tourApiRestClient)과 동일한 관용 설정 — 알 수 없는 필드/빈 문자열 허용
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    @Test
    @DisplayName("areaBasedList2 정상 응답을 파싱해 아이템 필드와 totalCount를 매핑한다")
    void parseNormalResponse() throws Exception {
        String json = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
            "body":{"items":{"item":[
              {"addr1":"전라남도 신안군 흑산면 가거도길 38-2","addr2":"","areacode":"38",
               "contentid":"127480","contenttypeid":"12",
               "firstimage":"http://tong.visitkorea.or.kr/a.jpg","firstimage2":"http://tong.visitkorea.or.kr/b.jpg",
               "mapx":"125.1263860145","mapy":"34.0520609879","tel":"","title":"가거도","modifiedtime":"20251124134437"}
            ]},"numOfRows":1,"pageNo":1,"totalCount":12694}}}
            """;

        KorServiceResponse response = mapper.readValue(json, KorServiceResponse.class);

        assertThat(response.response().header().resultCode()).isEqualTo("0000");
        KorServiceResponse.Body body = response.response().body();
        assertThat(body.totalCountValue()).isEqualTo(12694);

        List<TourApiPlaceItem> items = body.itemList();
        assertThat(items).hasSize(1);
        TourApiPlaceItem item = items.get(0);
        assertThat(item.contentId()).isEqualTo("127480");
        assertThat(item.title()).isEqualTo("가거도");
        assertThat(item.mapX()).isEqualTo("125.1263860145");
        assertThat(item.mapY()).isEqualTo("34.0520609879");
        assertThat(item.firstImage()).isEqualTo("http://tong.visitkorea.or.kr/a.jpg");
        assertThat(item.fullAddress()).isEqualTo("전라남도 신안군 흑산면 가거도길 38-2");
    }

    @Test
    @DisplayName("데이터 없음(items가 빈 문자열) 응답은 빈 리스트로 처리한다")
    void parseEmptyItems() throws Exception {
        String json = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
            "body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}
            """;

        KorServiceResponse response = mapper.readValue(json, KorServiceResponse.class);

        assertThat(response.response().body().itemList()).isEmpty();
        assertThat(response.response().body().totalCountValue()).isZero();
    }

    @Test
    @DisplayName("addr2가 있으면 addr1과 합쳐 전체 주소를 만든다")
    void fullAddressWithAddr2() {
        TourApiPlaceItem item = new TourApiPlaceItem(
                "1", "이름", "서울특별시 중구 세종대로", "110", "127.0", "37.5",
                null, null, null, null, null);
        assertThat(item.fullAddress()).isEqualTo("서울특별시 중구 세종대로 110");
    }
}
