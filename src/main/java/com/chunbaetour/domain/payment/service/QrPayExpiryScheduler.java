package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.payment.repository.QrPayRequestRepository;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * QR 결제 만료 스케줄러 (STORY-15).
 *
 * [왜 필요한가]
 * 사용자가 QR 결제 요청(STORY-13)을 생성하면 expiredAt = 생성시각 + 5분으로 저장된다.
 * 상인이 5분 내에 승인/거절하지 않으면 해당 요청은 PENDING 상태로 무한 잔류하게 된다.
 * PENDING 상태가 남아있으면 pendingKey unique 제약으로 인해 동일 사용자·가게의 재결제가 불가능하므로
 * 만료된 요청을 주기적으로 EXPIRED로 전환하여 사용자가 다시 결제할 수 있도록 해제해야 한다.
 *
 * [왜 60초 주기인가]
 * QR 결제 만료 단위가 5분(300초)이므로 1분(60초) 주기면 최대 오차 60초 내로 만료 처리된다.
 * 만료 후 사용자 UX에 큰 영향 없는 수준. 10초 미만으로 줄이면 DB 부하 대비 실익 없음.
 * fixedDelay 사용 — 이전 실행이 완료된 후 60초 뒤 재실행하므로 DB가 느릴 때 중첩 실행 방지.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QrPayExpiryScheduler {

    private static final long EXPIRY_CHECK_INTERVAL_MS = 60_000;

    private final QrPayRequestRepository qrPayRequestRepository;
    private final Clock clock;

    /**
     * 만료된 PENDING QR 결제 요청 EXPIRED 일괄 처리.
     * status = PENDING AND expiredAt < now 조건으로 단일 UPDATE 쿼리 실행.
     * pendingKey = null 함께 처리 — unique 제약 해제로 만료 후 재결제 허용.
     */
    @Scheduled(fixedDelay = EXPIRY_CHECK_INTERVAL_MS)
    @Transactional
    public void expireOverdueRequests() {
        // 현재 시각 기준으로 expiredAt이 지난 PENDING 요청 일괄 EXPIRED 전환
        LocalDateTime now = LocalDateTime.now(clock);
        // status=PENDING AND expiredAt < now 조건의 행을 EXPIRED로 UPDATE, 처리 건수 반환
        int count = qrPayRequestRepository.bulkExpireOverdue(QrPayStatus.PENDING, QrPayStatus.EXPIRED, now);
        // 실제 만료 처리된 건이 있을 때만 로그 출력 — 0건이면 정상 대기 상태이므로 로그 생략
        if (count > 0) {
            log.info("[QR 만료 스케줄러] {}건 EXPIRED 처리 완료 (기준 시각: {})", count, now);
        }
    }
}
