package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;

import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 충전 직렬화 분산락의 트랜잭션 완료 후 해제 가드 (KAN-293 리뷰 권장 1순위).
 *
 * <p>{@code ChargeService.charge()}는 finally가 아니라 {@code afterCompletion(status)}에서 락을 푼다 —
 * 다음 요청이 확정된(커밋된) PENDING을 보도록 트랜잭션 완료 이후로 해제를 미루기 위함이다. 이 분기는
 * 트랜잭션 컨텍스트가 살아있을 때만 동작하므로 mock 단위 테스트(트랜잭션 밖 finally 분기)로는 검증되지 않는다.
 *
 * <p>핵심 계약: <b>commit·rollback 모두</b>에서 락이 풀려 같은 사용자의 재시도가 락 대기 초과(PAY_008) 없이
 * 곧바로 락을 재획득할 수 있어야 한다(재시도 데드락 부재). 실Redisson + 실트랜잭션으로 검증한다.
 */
@SpringBootTest
class ChargeLockReleaseIntegrationTest extends AbstractIntegrationTest {

    private static final long USER_ID = 9200L;
    /** ChargeService.CHARGE_LOCK_KEY("charge:lock:%d")와 동일 포맷 — 사용자 단위 락 키. */
    private static final String LOCK_KEY = "charge:lock:" + USER_ID;

    @Autowired
    private ChargeService chargeService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 외부 PG 사전등록은 락 해제 검증과 무관 — 성공/실패를 stub로 제어한다(실제 PortOne 호출 차단)
    @MockitoBean
    private PaymentGatewayClient paymentGatewayClient;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM payment_orders WHERE user_id = ?", USER_ID);
    }

    @Test
    @DisplayName("charge() 트랜잭션 롤백 시 afterCompletion이 락을 해제해 재시도가 데드락 없이 성공한다")
    void chargeRollback_releasesLock_allowingRetry() {
        // 1차: PG 사전등록 실패 → RuntimeException 전파 → 트랜잭션 롤백. 2차부터는 정상.
        willThrow(new RuntimeException("PG down"))
                .willDoNothing()
                .given(paymentGatewayClient).preRegister(anyString(), anyLong());

        assertThatThrownBy(() -> chargeService.charge(USER_ID, "idem-" + UUID.randomUUID(),
                new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(RuntimeException.class);

        // 롤백 경로(afterCompletion)에서 락이 풀렸어야 한다 — 안 풀렸으면 재시도가 10s 대기 후 PAY_008로 샌다
        assertThat(redissonClient.getLock(LOCK_KEY).isLocked()).isFalse();

        // 2차 재시도: 락 재획득 가능 → 정상 완료(데드락 부재)
        assertThatCode(() -> chargeService.charge(USER_ID, "idem-" + UUID.randomUUID(),
                new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .doesNotThrowAnyException();
        assertThat(redissonClient.getLock(LOCK_KEY).isLocked()).isFalse();
    }

    @Test
    @DisplayName("charge() 트랜잭션 커밋 시에도 afterCompletion이 락을 해제한다")
    void chargeCommit_releasesLock() {
        willDoNothing().given(paymentGatewayClient).preRegister(anyString(), anyLong());

        assertThatCode(() -> chargeService.charge(USER_ID, "idem-" + UUID.randomUUID(),
                new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .doesNotThrowAnyException();

        // 커밋 후 락 해제 — 같은 사용자의 다음 충전이 곧바로 진입 가능
        assertThat(redissonClient.getLock(LOCK_KEY).isLocked()).isFalse();
    }
}
