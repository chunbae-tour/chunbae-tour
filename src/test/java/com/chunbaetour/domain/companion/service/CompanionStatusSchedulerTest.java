package com.chunbaetour.domain.companion.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanionStatusSchedulerTest {

    @Mock private CompanionService companionService;

    @InjectMocks private CompanionStatusScheduler companionStatusScheduler;

    // 정상 동작 — companionService.endExpiredCompanions() 호출
    @Test
    @DisplayName("동행 ENDED 전환 배치 — companionService.endExpiredCompanions() 호출")
    void endExpiredCompanions_callsService() {
        given(companionService.endExpiredCompanions()).willReturn(3);

        companionStatusScheduler.endExpiredCompanions();

        verify(companionService).endExpiredCompanions();
    }

    // 서비스에서 예외 발생 시에도 스케줄러는 예외를 전파하지 않음(로깅 후 종료)
    @Test
    @DisplayName("동행 ENDED 전환 배치 — 서비스 예외 발생해도 전파하지 않음")
    void endExpiredCompanions_whenServiceThrows_doesNotPropagate() {
        willThrow(new RuntimeException("DB error")).given(companionService).endExpiredCompanions();

        assertThatCode(() -> companionStatusScheduler.endExpiredCompanions())
                .doesNotThrowAnyException();
        verify(companionService).endExpiredCompanions();
    }
}
