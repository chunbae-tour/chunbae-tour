package com.chunbaetour.domain.place.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlaceSyncStateTest {

    @Test
    @DisplayName("yyyyMMddHHmmss(14자리 숫자) 경계는 정상 저장된다")
    void updateWithValidTimestamp() {
        PlaceSyncState state = PlaceSyncState.init();

        state.updateLastModifiedTime("20260101123045");

        assertThat(state.getLastModifiedTime()).isEqualTo("20260101123045");
    }

    @Test
    @DisplayName("null 경계는 거부한다")
    void rejectNull() {
        PlaceSyncState state = PlaceSyncState.init();

        assertThatThrownBy(() -> state.updateLastModifiedTime(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "2026", "2026010112304", "202601011230456", "2026010112304a", "2026-01-01 12:30"})
    @DisplayName("형식이 yyyyMMddHHmmss(14자리 숫자)가 아니면 거부한다")
    void rejectInvalidFormat(String invalid) {
        PlaceSyncState state = PlaceSyncState.init();

        assertThatThrownBy(() -> state.updateLastModifiedTime(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
