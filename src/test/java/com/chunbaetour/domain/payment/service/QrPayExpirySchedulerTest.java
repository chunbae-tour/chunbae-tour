package com.chunbaetour.domain.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.payment.repository.QrPayRequestRepository;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QrPayExpirySchedulerTest {

    @Mock
    private QrPayRequestRepository qrPayRequestRepository;

    // 고정 시각 — 2026-05-27T00:00:00Z
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private QrPayExpiryScheduler qrPayExpiryScheduler;

    // ── expireOverdueRequests ─────────────────────────────────────────────────

    @Test
    @DisplayName("만료 스케줄러 — 만료된 요청 존재 시 bulkExpireOverdue 호출")
    void expireOverdueRequests_callsBulkExpire() {
        // given — 3건 만료 처리
        LocalDateTime expectedNow = LocalDateTime.of(2026, 5, 27, 0, 0, 0);
        given(qrPayRequestRepository.bulkExpireOverdue(QrPayStatus.PENDING, QrPayStatus.EXPIRED, expectedNow))
                .willReturn(3);

        // when
        qrPayExpiryScheduler.expireOverdueRequests();

        // then — PENDING → EXPIRED, 고정 시각 기준 호출
        verify(qrPayRequestRepository).bulkExpireOverdue(
                eq(QrPayStatus.PENDING),
                eq(QrPayStatus.EXPIRED),
                eq(expectedNow)
        );
    }

    @Test
    @DisplayName("만료 스케줄러 — 만료된 요청 없을 때도 bulkExpireOverdue 호출")
    void expireOverdueRequests_noOverdue_stillCalls() {
        // given — 0건 (만료 대상 없음)
        given(qrPayRequestRepository.bulkExpireOverdue(any(), any(), any())).willReturn(0);

        // when
        qrPayExpiryScheduler.expireOverdueRequests();

        // then — 정상 호출, 예외 없음
        verify(qrPayRequestRepository).bulkExpireOverdue(
                eq(QrPayStatus.PENDING),
                eq(QrPayStatus.EXPIRED),
                any(LocalDateTime.class)
        );
    }
}
