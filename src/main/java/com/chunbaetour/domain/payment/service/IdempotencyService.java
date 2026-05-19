package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
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

    public void checkAndMark(String idempotencyKey) {
        String key = KEY_PREFIX + idempotencyKey;
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isNew)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT_REQUEST);
        }
    }
}
