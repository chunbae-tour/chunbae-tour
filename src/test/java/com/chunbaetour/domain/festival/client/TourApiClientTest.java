package com.chunbaetour.domain.festival.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.chunbaetour.domain.common.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TourApiClientTest {

    private static final String BASE_URL = "https://example.com/festival";
    // items 빈 페이지 — fetchAll이 1페이지만 받고 종료
    private static final String EMPTY_OK_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"OK"},
            "body":{"items":[],"numOfRows":"100","pageNo":"1","totalCount":"0"}}}
            """;

    private record Fixture(TourApiClient client, MockRestServiceServer server) {}

    // initialBackoffMillis=1 → 재시도 대기 무시 가능 수준
    private Fixture fixture(int maxAttempts) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TourApiClient client = new TourApiClient(builder.build(), "test-key", BASE_URL, maxAttempts, 1L);
        return new Fixture(client, server);
    }

    @Test
    void 일시적_5xx면_재시도_후_성공() {
        Fixture f = fixture(3);
        // 503 한 번 → 200 한 번 (순서대로 매칭)
        f.server().expect(times(1), requestTo(startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        f.server().expect(times(1), requestTo(startsWith(BASE_URL)))
                .andRespond(withSuccess(EMPTY_OK_BODY, MediaType.APPLICATION_JSON));

        List<TourApiFestivalItem> result = f.client().fetchAll();

        assertThat(result).isEmpty();
        f.server().verify();
    }

    @Test
    void 비일시적_4xx면_재시도_없이_실패() {
        Fixture f = fixture(3);
        f.server().expect(times(1), requestTo(startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> f.client().fetchAll())
                .isInstanceOf(BusinessException.class);

        f.server().verify(); // 정확히 1회만 호출 — 재시도 안 함
    }

    @Test
    void 서버오류_maxAttempts_초과하면_실패() {
        Fixture f = fixture(3);
        f.server().expect(times(3), requestTo(startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> f.client().fetchAll())
                .isInstanceOf(BusinessException.class);

        f.server().verify(); // 3회 모두 소진
    }
}
