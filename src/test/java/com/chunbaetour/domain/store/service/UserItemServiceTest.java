package com.chunbaetour.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.store.dto.response.UserItemResponse;
import com.chunbaetour.domain.store.entity.UserItem;
import com.chunbaetour.domain.store.repository.UserItemRepository;
import com.chunbaetour.domain.store.type.UserItemStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserItemServiceTest {

    @Mock private UserItemRepository userItemRepository;
    @InjectMocks private UserItemService userItemService;

    private static final Long USER_ID = 1L;

    private UserItem createItem(Long itemId) {
        UserItem item = mock(UserItem.class);
        given(item.getId()).willReturn(itemId);
        given(item.getOrderId()).willReturn(100L);
        given(item.getProductId()).willReturn(10L);
        given(item.getProductName()).willReturn("테스트 아이템");
        given(item.getStatus()).willReturn(UserItemStatus.AVAILABLE);
        given(item.getExpiresAt()).willReturn(LocalDate.of(2026, 12, 31));
        return item;
    }

    @Test
    @DisplayName("내 보유 아이템 조회 — 첫 페이지 (hasNext=false)")
    void getMyItems_firstPage() {
        // given
        UserItem item1 = createItem(2L);
        UserItem item2 = createItem(1L);
        given(userItemRepository.findItemsByUserIdWithCursor(eq(USER_ID), eq(null), any()))
                .willReturn(List.of(item1, item2));

        // when
        CursorPageResponse<UserItemResponse> result = userItemService.getMyItems(USER_ID, null, 20);

        // then
        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("내 보유 아이템 조회 — 다음 페이지 존재 (hasNext=true, cursor 반환)")
    void getMyItems_hasNext() {
        // given: size=2 요청, size+1=3개 반환 → hasNext=true
        UserItem item1 = createItem(3L);
        UserItem item2 = createItem(2L);
        UserItem item3 = mock(UserItem.class); // hasNext 판별용 sentinel — DTO 변환 대상 아님
        given(userItemRepository.findItemsByUserIdWithCursor(eq(USER_ID), eq(null), any()))
                .willReturn(List.of(item1, item2, item3));

        // when
        CursorPageResponse<UserItemResponse> result = userItemService.getMyItems(USER_ID, null, 2);

        // then — size=2만 반환, hasNext=true, nextCursor는 마지막 항목(item2) ID 기반
        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("내 보유 아이템 조회 — 빈 목록 (hasNext=false)")
    void getMyItems_empty() {
        // given
        given(userItemRepository.findItemsByUserIdWithCursor(eq(USER_ID), eq(null), any()))
                .willReturn(List.of());

        // when
        CursorPageResponse<UserItemResponse> result = userItemService.getMyItems(USER_ID, null, 20);

        // then
        assertThat(result.content()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("내 보유 아이템 조회 — cursor 전달 시 cursor 이후 항목만 반환")
    void getMyItems_withCursor() {
        // given: cursor=encoded(5) 전달
        String cursor = CursorUtils.encode(5L);
        UserItem item = createItem(4L);
        given(userItemRepository.findItemsByUserIdWithCursor(eq(USER_ID), eq(5L), any()))
                .willReturn(List.of(item));

        // when
        CursorPageResponse<UserItemResponse> result = userItemService.getMyItems(USER_ID, cursor, 20);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).itemId()).isEqualTo(4L);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("내 보유 아이템 조회 — 잘못된 cursor 형식 → INVALID_CURSOR 예외")
    void getMyItems_invalidCursor_throwsInvalidCursor() {
        String invalidCursor = "not-base64!!";

        assertThatThrownBy(() -> userItemService.getMyItems(USER_ID, invalidCursor, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }
}
