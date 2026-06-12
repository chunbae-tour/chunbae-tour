package com.chunbaetour.domain.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.SanctionType;
import com.chunbaetour.domain.report.entity.UserSanction;
import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link UserSanctionRepository#findActiveSanctionsByUserIdAndTargetType} 쿼리 통합 테스트 (PR4).
 * 활성/해제/만료/타도메인/PERMANENT 필터 정확성 검증.
 */
@SpringBootTest
class UserSanctionRepositoryTest extends AbstractIntegrationTest {

    private static final long USER_ID = 5000L;
    private static final long OTHER_USER = 5001L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 12, 12, 0, 0);

    @Autowired
    private UserSanctionRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    private UserSanction save(long userId, ReportTargetType type, SanctionType sanction,
                              LocalDateTime endedAt, LocalDateTime releasedAt) {
        UserSanction s = UserSanction.create(userId, 1L, type, sanction, "테스트 제재",
                NOW.minusDays(1), endedAt);
        if (releasedAt != null) {
            s.release(99L, releasedAt);
        }
        return repository.save(s);
    }

    @Test
    @DisplayName("활성 제재만 반환 — 해제·만료·타도메인·타유저 제외")
    void findActive_filters_correctly() {
        // 활성 (대상): POST_FREE, endedAt 미래
        save(USER_ID, ReportTargetType.POST_FREE, SanctionType.SUSPEND_7D, NOW.plusDays(7), null);
        // 해제됨 → 제외
        save(USER_ID, ReportTargetType.POST_FREE, SanctionType.SUSPEND_7D, NOW.plusDays(7), NOW.minusHours(1));
        // 만료됨 (endedAt 과거) → 제외
        save(USER_ID, ReportTargetType.POST_FREE, SanctionType.SUSPEND_7D, NOW.minusHours(1), null);
        // 다른 도메인 → 제외
        save(USER_ID, ReportTargetType.COMMENT, SanctionType.SUSPEND_7D, NOW.plusDays(7), null);
        // 다른 유저 → 제외
        save(OTHER_USER, ReportTargetType.POST_FREE, SanctionType.SUSPEND_7D, NOW.plusDays(7), null);

        List<UserSanction> result = repository.findActiveSanctionsByUserIdAndTargetType(
                USER_ID, ReportTargetType.POST_FREE, NOW);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTargetType()).isEqualTo(ReportTargetType.POST_FREE);
        assertThat(result.get(0).getReleasedAt()).isNull();
    }

    @Test
    @DisplayName("PERMANENT(endedAt null)도 활성으로 반환")
    void findActive_includes_permanent() {
        save(USER_ID, ReportTargetType.REVIEW, SanctionType.PERMANENT, null, null);

        List<UserSanction> result = repository.findActiveSanctionsByUserIdAndTargetType(
                USER_ID, ReportTargetType.REVIEW, NOW);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSanctionType()).isEqualTo(SanctionType.PERMANENT);
    }
}
