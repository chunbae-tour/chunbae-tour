package com.chunbaetour.domain.festival.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FestivalTest {

    private static final LocalDate JUN_18 = LocalDate.of(2026, 6, 18);
    private static final LocalDate JUN_23 = LocalDate.of(2026, 6, 23);

    // ── create ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create — 유효한 입력으로 축제 생성 성공")
    void create_성공() {
        Festival f = Festival.create("서울 축제", "설명", "서울", "서울시 강남구", JUN_18, JUN_23, null, null, FestivalStatus.ACTIVE);

        assertThat(f.getName()).isEqualTo("서울 축제");
        assertThat(f.getRegion()).isEqualTo("서울");
        assertThat(f.getStatus()).isEqualTo(FestivalStatus.ACTIVE);
        assertThat(f.getStartDate()).isEqualTo(JUN_18);
        assertThat(f.getEndDate()).isEqualTo(JUN_23);
    }

    @Test
    @DisplayName("create — status null이면 ACTIVE 기본값 적용")
    void create_status_null_시_ACTIVE_기본값() {
        Festival f = Festival.create("서울 축제", null, "서울", "서울시 강남구", JUN_18, JUN_23, null, null, null);

        assertThat(f.getStatus()).isEqualTo(FestivalStatus.ACTIVE);
    }

    @Test
    @DisplayName("create — startDate가 endDate 이후면 BusinessException")
    void create_startDate가_endDate_이후_예외() {
        assertThatThrownBy(() ->
                Festival.create("서울 축제", null, "서울", "서울시 강남구", JUN_23, JUN_18, null, null, FestivalStatus.ACTIVE)
        ).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("create — name blank이면 BusinessException")
    void create_name_blank_예외() {
        assertThatThrownBy(() ->
                Festival.create("   ", null, "서울", "서울시 강남구", JUN_18, JUN_23, null, null, FestivalStatus.ACTIVE)
        ).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("create — region null이면 BusinessException")
    void create_region_null_예외() {
        assertThatThrownBy(() ->
                Festival.create("서울 축제", null, null, "서울시 강남구", JUN_18, JUN_23, null, null, FestivalStatus.ACTIVE)
        ).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("create — startDate null이면 BusinessException")
    void create_startDate_null_예외() {
        assertThatThrownBy(() ->
                Festival.create("서울 축제", null, "서울", "서울시 강남구", null, JUN_23, null, null, FestivalStatus.ACTIVE)
        ).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("create — startDate == endDate 허용 (당일 행사)")
    void create_startDate_endDate_동일_성공() {
        Festival f = Festival.create("당일 행사", null, "서울", "서울시 강남구", JUN_18, JUN_18, null, null, FestivalStatus.ACTIVE);

        assertThat(f.getStartDate()).isEqualTo(f.getEndDate());
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update — 유효한 값으로 모든 필드 교체")
    void update_필드_변경() {
        Festival f = Festival.create("원래 이름", null, "서울", "서울시 강남구", JUN_18, JUN_23, null, null, FestivalStatus.ACTIVE);
        LocalDate newStart = LocalDate.of(2026, 8, 1);
        LocalDate newEnd = LocalDate.of(2026, 8, 10);

        f.update("새 이름", "새 설명", "부산", "부산시 해운대구", newStart, newEnd, null, null, FestivalStatus.HIDDEN);

        assertThat(f.getName()).isEqualTo("새 이름");
        assertThat(f.getDescription()).isEqualTo("새 설명");
        assertThat(f.getRegion()).isEqualTo("부산");
        assertThat(f.getAddress()).isEqualTo("부산시 해운대구");
        assertThat(f.getStartDate()).isEqualTo(newStart);
        assertThat(f.getEndDate()).isEqualTo(newEnd);
        assertThat(f.getStatus()).isEqualTo(FestivalStatus.HIDDEN);
    }

    @Test
    @DisplayName("update — status null이면 BusinessException")
    void update_status_null_예외() {
        Festival f = Festival.create("서울 축제", null, "서울", "서울시 강남구", JUN_18, JUN_23, null, null, FestivalStatus.ACTIVE);

        assertThatThrownBy(() ->
                f.update("서울 축제", null, "서울", "서울시 강남구", JUN_18, JUN_23, null, null, null)
        ).isInstanceOf(BusinessException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete — soft delete, status가 DELETED로 변경")
    void delete_status가_DELETED_됨() {
        Festival f = Festival.create("서울 축제", null, "서울", "서울시 강남구", JUN_18, JUN_23, null, null, FestivalStatus.ACTIVE);

        f.delete();

        assertThat(f.getStatus()).isEqualTo(FestivalStatus.DELETED);
    }

    // ── isActive ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("isActive — ACTIVE이면 true")
    void isActive_ACTIVE_true() {
        Festival f = Festival.create("서울 축제", null, "서울", "서울시 강남구", JUN_18, JUN_23, null, null, FestivalStatus.ACTIVE);

        assertThat(f.isActive()).isTrue();
    }

    @Test
    @DisplayName("isActive — HIDDEN이면 false")
    void isActive_HIDDEN_false() {
        Festival f = Festival.create("서울 축제", null, "서울", "서울시 강남구", JUN_18, JUN_23, null, null, FestivalStatus.HIDDEN);

        assertThat(f.isActive()).isFalse();
    }

    @Test
    @DisplayName("isActive — delete 후 DELETED이면 false")
    void isActive_DELETED_false() {
        Festival f = Festival.create("서울 축제", null, "서울", "서울시 강남구", JUN_18, JUN_23, null, null, FestivalStatus.ACTIVE);
        f.delete();

        assertThat(f.isActive()).isFalse();
    }
}
