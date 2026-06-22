package com.chunbaetour.domain.common.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.util.CursorUtils;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CursorPageResponseTest {

    private record Item(long id, String name) {
        long getId() {
            return id;
        }
    }

    @Test
    @DisplayName("of — raw가 size+1개면 hasNext=true, 초과분을 잘라내고 마지막 노출 항목으로 nextCursor를 만든다")
    void of_rawExceedsSize_hasNextAndCursor() {
        List<Item> raw = List.of(new Item(30L, "a"), new Item(20L, "b"), new Item(10L, "c"));

        CursorPageResponse<String> result =
                CursorPageResponse.of(raw, 2, Item::name, Item::getId);

        assertThat(result.content()).containsExactly("a", "b");
        assertThat(result.hasNext()).isTrue();
        assertThat(result.size()).isEqualTo(2);
        // nextCursor는 노출된 마지막 항목(id=20)의 인코딩
        assertThat(CursorUtils.decode(result.nextCursor())).isEqualTo(20L);
    }

    @Test
    @DisplayName("of — raw가 size 이하이면 hasNext=false, nextCursor=null")
    void of_rawWithinSize_noNext() {
        List<Item> raw = List.of(new Item(30L, "a"), new Item(20L, "b"));

        CursorPageResponse<String> result =
                CursorPageResponse.of(raw, 2, Item::name, Item::getId);

        assertThat(result.content()).containsExactly("a", "b");
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("of — 빈 목록이면 빈 content·hasNext=false·nextCursor=null, size는 요청값(2)을 echo")
    void of_empty_returnsEmpty() {
        CursorPageResponse<String> result =
                CursorPageResponse.of(List.<Item>of(), 2, Item::name, Item::getId);

        assertThat(result.content()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        // size 필드는 실제 반환 개수(0)가 아니라 요청 size(2)를 echo (팀 표준)
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("of — 정확히 size개면 hasNext=false (경계값)")
    void of_exactlySize_noNext() {
        List<Item> raw = List.of(new Item(10L, "only"));

        CursorPageResponse<String> result =
                CursorPageResponse.of(raw, 1, Item::name, Item::getId);

        assertThat(result.content()).containsExactly("only");
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("of — 마지막 페이지에 요청보다 적게 남아도 size는 요청값을 echo한다")
    void of_partialLastPage_echoesRequestedSize() {
        List<Item> raw = List.of(new Item(30L, "a"), new Item(20L, "b"));  // 2개

        CursorPageResponse<String> result =
                CursorPageResponse.of(raw, 5, Item::name, Item::getId);     // size=5 요청

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        // 실제 반환 2개여도 size 필드는 요청한 5
        assertThat(result.size()).isEqualTo(5);
    }

    @Test
    @DisplayName("of — size<1이면 BusinessException(INVALID_REQUEST)으로 fail-fast (page.get(-1) 500 방지)")
    void of_sizeBelowOne_throws() {
        List<Item> raw = List.of(new Item(10L, "a"));

        assertThatThrownBy(() -> CursorPageResponse.of(raw, 0, Item::name, Item::getId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // ── ofAssembled (KAN-325) — 조립형: 이미 잘라낸 content + 직접 인코딩 커서 ──────────────

    @Test
    @DisplayName("ofAssembled — 전달한 content·nextCursor·hasNext를 그대로 싣고, size는 요청값을 echo한다")
    void ofAssembled_passesThroughAndEchoesSize() {
        List<String> content = List.of("a", "b");

        CursorPageResponse<String> result =
                CursorPageResponse.ofAssembled(content, "cursor-xyz", true, 10);

        assertThat(result.content()).containsExactly("a", "b");
        assertThat(result.nextCursor()).isEqualTo("cursor-xyz");
        assertThat(result.hasNext()).isTrue();
        // 실제 개수(2)가 아니라 요청 size(10)를 echo — 직접 생성 시 content.size()를 넘기던 split-brain 제거
        assertThat(result.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("ofAssembled — 빈 마지막 페이지여도 size는 요청값을 echo (이전 0 하드코딩 대체)")
    void ofAssembled_emptyPage_echoesRequestedSize() {
        CursorPageResponse<String> result =
                CursorPageResponse.ofAssembled(List.of(), null, false, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("ofAssembled — size<1이면 계산형 of()와 동일하게 BusinessException(INVALID_REQUEST)")
    void ofAssembled_sizeBelowOne_throws() {
        assertThatThrownBy(() -> CursorPageResponse.ofAssembled(List.of("a"), null, false, 0))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
