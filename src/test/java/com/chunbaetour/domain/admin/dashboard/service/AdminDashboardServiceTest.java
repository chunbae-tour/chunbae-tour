package com.chunbaetour.domain.admin.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

import com.chunbaetour.domain.admin.dashboard.dto.response.AdminDashboardResponse;
import com.chunbaetour.domain.admin.user.service.AdminUserService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link AdminDashboardService} 단위 테스트 (KAN-181 Admin S03).
 *
 * <p>조합(AdminUserService 위임) + Redis 캐시 hit 시 DB 1회 + Redis 장애 graceful 폴백을 검증한다.
 * ObjectMapper는 mock으로 두어 직렬화/역직렬화를 결정적으로 통제한다(Jackson3 동작은 통합 테스트 커버).
 */
@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private AdminUserService adminUserService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private ObjectMapper objectMapper;

    private AdminDashboardService service() {
        return new AdminDashboardService(adminUserService, redisTemplate, objectMapper);
    }

    private void stubCounts(long total, long today, long suspended) {
        given(adminUserService.getTotalUsers()).willReturn(total);
        given(adminUserService.getNewUsersToday()).willReturn(today);
        given(adminUserService.getSuspendedUsers()).willReturn(suspended);
    }

    @Test
    @DisplayName("캐시 miss → AdminUserService 3종 조합 결과 매핑")
    void getSummary_cacheMiss_combinesCounts() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(anyString())).willReturn(null);
        stubCounts(42L, 7L, 3L);

        AdminDashboardResponse response = service().getSummary();

        assertThat(response.totalUsers()).isEqualTo(42L);
        assertThat(response.newUsersToday()).isEqualTo(7L);
        assertThat(response.suspendedUsers()).isEqualTo(3L);
    }

    @Test
    @DisplayName("2회 호출: 2번째는 캐시 hit → DB 조합 1회만")
    void getSummary_secondCall_hitsCache_dbCalledOnce() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        stubCounts(10L, 2L, 1L);

        AdminDashboardResponse cachedValue = new AdminDashboardResponse(10L, 2L, 1L);
        AtomicReference<String> store = new AtomicReference<>();
        given(valueOps.get(anyString())).willAnswer(inv -> store.get());
        willAnswer(inv -> {
            store.set("CACHED_JSON");
            return null;
        }).given(valueOps).set(anyString(), anyString(), any());
        given(objectMapper.writeValueAsString(any())).willReturn("CACHED_JSON");
        given(objectMapper.readValue("CACHED_JSON", AdminDashboardResponse.class)).willReturn(cachedValue);

        AdminDashboardService service = service();
        AdminDashboardResponse first = service.getSummary();   // miss → DB → 캐시 저장
        AdminDashboardResponse second = service.getSummary();  // hit → 역직렬화

        assertThat(first.totalUsers()).isEqualTo(10L);
        assertThat(second).isEqualTo(first);
        then(adminUserService).should(times(1)).getTotalUsers();
    }

    @Test
    @DisplayName("Redis 조회 장애 → 예외 전파 없이 DB 폴백 (graceful)")
    void getSummary_redisFailure_fallsBackToDb() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(anyString())).willThrow(new RuntimeException("redis down"));
        willThrow(new RuntimeException("redis down"))
                .given(valueOps).set(anyString(), anyString(), any());
        stubCounts(5L, 1L, 0L);

        AdminDashboardResponse response = service().getSummary();

        assertThat(response.totalUsers()).isEqualTo(5L);
        assertThat(response.newUsersToday()).isEqualTo(1L);
        assertThat(response.suspendedUsers()).isEqualTo(0L);
    }
}
