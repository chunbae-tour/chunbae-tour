package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.exception.PaymentException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";
    private static final long TTL_HOURS = 24;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 동일한 멱등성 키로 중복 요청이 들어오면 PAY_007을 던진다.
     *
     * setIfAbsent: Redis에 키가 없을 때만 저장 (원자적 연산)
     *   - 처음 요청 → 키 저장 성공 → isNew=true → 통과
     *   - 재요청    → 키 이미 존재 → 저장 실패  → isNew=false → PAY_007
     *   - 24시간 후 TTL 만료 → 키 자동 삭제 → 동일 키로 재요청 가능
     */
    public void checkAndMark(String idempotencyKey) {
        String key = KEY_PREFIX + idempotencyKey;
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isNew)) {
            throw new PaymentException(ErrorCode.DUPLICATE_PAYMENT_REQUEST);
        }
    }

    public void unmark(String idempotencyKey) {
        stringRedisTemplate.delete(KEY_PREFIX + idempotencyKey);
    }
}
