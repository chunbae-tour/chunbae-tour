package com.chunbaetour.domain.common.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("of — size<1이면 IllegalArgumentException으로 fail-fast (page.get(-1) 500 방지)")
    void of_sizeBelowOne_throws() {
        List<Item> raw = List.of(new Item(10L, "a"));

        assertThatThrownBy(() -> CursorPageResponse.of(raw, 0, Item::name, Item::getId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
