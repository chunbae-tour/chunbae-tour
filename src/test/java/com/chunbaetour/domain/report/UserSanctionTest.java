package com.chunbaetour.domain.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.SanctionType;
import com.chunbaetour.domain.report.entity.UserSanction;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserSanctionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 11, 12, 0, 0);

    @Test
    void release_sets_releasedAt_and_releasedBy() {
        UserSanction sanction = UserSanction.create(
                1L, 10L, ReportTargetType.USER, SanctionType.SUSPEND_7D,
                "테스트 제재", NOW.minusDays(1), NOW.plusDays(6));

        sanction.release(99L, NOW);

        assertThat(sanction.getReleasedAt()).isEqualTo(NOW);
        assertThat(sanction.getReleasedBy()).isEqualTo(99L);
    }

    @Test
    void release_with_null_adminId_records_auto_release() {
        UserSanction sanction = UserSanction.create(
                1L, 10L, ReportTargetType.COMMENT, SanctionType.SUSPEND_7D,
                "자동 만료", NOW.minusDays(8), NOW.minusHours(1));

        sanction.release(null, NOW);

        assertThat(sanction.getReleasedAt()).isEqualTo(NOW);
        assertThat(sanction.getReleasedBy()).isNull();
    }

    @Test
    void release_second_call_is_noop_first_values_preserved() {
        UserSanction sanction = UserSanction.create(
                1L, 10L, ReportTargetType.POST_FREE, SanctionType.WARNING,
                "1차 해제", NOW.minusDays(1), NOW);

        sanction.release(99L, NOW);
        LocalDateTime secondCallTime = NOW.plusMinutes(5);
        sanction.release(42L, secondCallTime);

        assertThat(sanction.getReleasedAt()).isEqualTo(NOW);
        assertThat(sanction.getReleasedBy()).isEqualTo(99L);
    }
}
