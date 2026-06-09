package com.chunbaetour.domain.store.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.store.type.UserItemStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserItemTest {

    @Test
    @DisplayName("use는 AVAILABLE 아이템을 USED로 전환하고 사용 정보를 기록한다")
    void use_available_success() {
        UserItem item = item(UserItemStatus.AVAILABLE);
        LocalDateTime usedAt = LocalDateTime.of(2026, 6, 8, 12, 0);

        item.use(usedAt, 20L);

        assertThat(item.getStatus()).isEqualTo(UserItemStatus.USED);
        assertThat(item.getUsedAt()).isEqualTo(usedAt);
        assertThat(item.getUsedShopId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("use는 이미 USED인 아이템이면 ITEM_ALREADY_USED 예외가 발생한다")
    void use_used_throwsAlreadyUsed() {
        UserItem item = item(UserItemStatus.USED);

        assertThatThrownBy(() -> item.use(LocalDateTime.now(), 20L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_ALREADY_USED);
    }

    @Test
    @DisplayName("use는 EXPIRED 아이템이면 ITEM_EXPIRED 예외가 발생한다")
    void use_expired_throwsItemExpired() {
        UserItem item = item(UserItemStatus.EXPIRED);

        assertThatThrownBy(() -> item.use(LocalDateTime.now(), 20L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_EXPIRED);
    }

    private UserItem item(UserItemStatus status) {
        Product product = Product.create("테스트 아이템", "설명", "쿠폰", 1000L,
                null, 10, "[]", "테스트 상점", 30, 1);
        ReflectionTestUtils.setField(product, "id", 100L);
        UserItem item = UserItem.create(1L, 200L, product, LocalDate.of(2026, 6, 8));
        ReflectionTestUtils.setField(item, "id", 10L);
        ReflectionTestUtils.setField(item, "status", status);
        return item;
    }
}
