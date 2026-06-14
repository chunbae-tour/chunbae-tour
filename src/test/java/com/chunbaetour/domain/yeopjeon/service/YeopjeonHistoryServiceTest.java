package com.chunbaetour.domain.yeopjeon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.yeopjeon.dto.response.YeopjeonHistoryResponse;
import com.chunbaetour.domain.yeopjeon.entity.YeopjeonHistory;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import com.chunbaetour.domain.yeopjeon.type.YeopjeonHistoryType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class YeopjeonHistoryServiceTest {

    @Mock
    private YeopjeonHistoryRepository yeopjeonHistoryRepository;

    @InjectMocks
    private YeopjeonHistoryService yeopjeonHistoryService;

    private YeopjeonHistory makeHistory(Long id, Long amount) {
        return makeHistory(id, YeopjeonHistoryType.CHARGE, amount);
    }

    private YeopjeonHistory makeHistory(Long id, YeopjeonHistoryType type, Long amount) {
        YeopjeonHistory history = YeopjeonHistory.create(1L, null, null, type, amount, amount, "테스트");
        ReflectionTestUtils.setField(history, "id", id);
        return history;
    }

    private String encode(long id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(id).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("cursor 없이 첫 페이지 조회 시 최신순으로 반환하고 hasNext=false이다")
    void getHistories_noCursor_returnsFirstPage() {
        List<YeopjeonHistory> rows = List.of(makeHistory(2L, 5000L), makeHistory(1L, 3000L));
        given(yeopjeonHistoryRepository.findByUserIdOrderByIdDesc(eq(1L), any(PageRequest.class)))
                .willReturn(rows);

        CursorPageResponse<YeopjeonHistoryResponse> response = yeopjeonHistoryService.getHistories(1L, null, 20);

        assertThat(response.content()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.content().get(0).amount()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("size+1개 결과 반환 시 hasNext=true이고 nextCursor가 세팅된다")
    void getHistories_noCursor_hasNextPage() {
        List<YeopjeonHistory> rows = List.of(
                makeHistory(3L, 1000L),
                makeHistory(2L, 2000L),
                makeHistory(1L, 3000L)
        );
        given(yeopjeonHistoryRepository.findByUserIdOrderByIdDesc(eq(1L), any(PageRequest.class)))
                .willReturn(rows);

        CursorPageResponse<YeopjeonHistoryResponse> response = yeopjeonHistoryService.getHistories(1L, null, 2);

        assertThat(response.hasNext()).isTrue();
        assertThat(response.content()).hasSize(2);
        assertThat(response.nextCursor()).isEqualTo(encode(2L));
    }

    @Test
    @DisplayName("cursor 있을 때 cursorId 이하의 내역을 조회한다")
    void getHistories_withCursor_queriesWithCursorId() {
        String cursor = encode(5L);
        given(yeopjeonHistoryRepository.findByUserIdAndIdLessThanOrderByIdDesc(eq(1L), eq(5L), any(PageRequest.class)))
                .willReturn(List.of(makeHistory(4L, 1000L)));

        CursorPageResponse<YeopjeonHistoryResponse> response = yeopjeonHistoryService.getHistories(1L, cursor, 20);

        verify(yeopjeonHistoryRepository).findByUserIdAndIdLessThanOrderByIdDesc(eq(1L), eq(5L), any(PageRequest.class));
        assertThat(response.content()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("잘못된 커서 전달 시 COMMON_002(INVALID_REQUEST)를 던진다")
    void getHistories_invalidCursor_throws_COMMON_002() {
        assertThatThrownBy(() -> yeopjeonHistoryService.getHistories(1L, "invalid-cursor!!", 20))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("음수 id를 인코딩한 cursor 전달 시 INVALID_REQUEST를 던진다")
    void getHistories_negativeCursor_throws_INVALID_REQUEST() {
        String negativeCursor = encode(-1L);
        assertThatThrownBy(() -> yeopjeonHistoryService.getHistories(1L, negativeCursor, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("빈 문자열 cursor 전달 시 INVALID_REQUEST를 던진다")
    void getHistories_emptyCursor_throws_INVALID_REQUEST() {
        assertThatThrownBy(() -> yeopjeonHistoryService.getHistories(1L, "", 20))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("결과가 없으면 빈 리스트를 반환하고 nextCursor는 null이다")
    void getHistories_emptyResult_returnsEmptyPage() {
        given(yeopjeonHistoryRepository.findByUserIdOrderByIdDesc(eq(1L), any(PageRequest.class)))
                .willReturn(Collections.emptyList());

        CursorPageResponse<YeopjeonHistoryResponse> response = yeopjeonHistoryService.getHistories(1L, null, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        // size는 실제 반환 개수(0)가 아니라 요청 size(20)를 echo (팀 표준)
        assertThat(response.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("이용 내역 금액은 타입에 따라 충전/받은 결제는 양수, 결제/환불은 음수로 반환한다")
    void getHistories_amountSign_normalizesByHistoryType() {
        List<YeopjeonHistory> rows = List.of(
                makeHistory(4L, YeopjeonHistoryType.CHARGE, 5_000L),
                makeHistory(3L, YeopjeonHistoryType.RECEIVED_PAYMENT, 3_000L),
                makeHistory(2L, YeopjeonHistoryType.PAYMENT, 2_000L),
                makeHistory(1L, YeopjeonHistoryType.REFUND, -1_000L)
        );
        given(yeopjeonHistoryRepository.findByUserIdOrderByIdDesc(eq(1L), any(PageRequest.class)))
                .willReturn(rows);

        CursorPageResponse<YeopjeonHistoryResponse> response = yeopjeonHistoryService.getHistories(1L, null, 20);

        assertThat(response.content())
                .extracting(YeopjeonHistoryResponse::amount)
                .containsExactly(5_000L, 3_000L, -2_000L, -1_000L);
    }
}
