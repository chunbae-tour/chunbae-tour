package com.chunbaetour.domain.cs.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.cs.dto.request.SupportRoomCloseRequest;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import com.chunbaetour.domain.notification.repository.NotificationRepository;
import com.chunbaetour.domain.notification.service.NotificationRedisPubSubService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * closeRoom 조건부 UPDATE 동시성 검증 — closeIfOpen(status != CLOSED) DB 행 잠금 실측.
 * 두 ADMIN 동시 호출 시 정확히 1건만 CLOSED 갱신 → 이벤트 단일 발행을 단위 테스트로는 검증 불가.
 */
@SpringBootTest
class SupportRoomCloseRaceConcurrencyTest extends AbstractIntegrationTest {

    // 동시성 테스트 범위 외 — Redis Pub/Sub 발행이 실제 리스너로 전파되지 않도록 차단
    @MockitoBean NotificationRedisPubSubService notificationRedisPubSubService;

    @Autowired private SupportRoomService supportRoomService;
    @Autowired private SupportRoomRepository supportRoomRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Long userId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        cleanup();
        Account user = accountRepository.saveAndFlush(
                Account.registerUser("cs-race@test.com", "hashedPw", "상담유저"));
        userId = user.getId();

        SupportRoom room = supportRoomRepository.saveAndFlush(
                SupportRoom.builder().userId(userId).build());
        roomId = room.getId();
    }

    @AfterEach
    void cleanup() {
        notificationRepository.deleteAll();
        supportRoomRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("두 ADMIN 동시 closeRoom → closeIfOpen 조건부 UPDATE로 정확히 1건만 성공, 알림 단일 생성")
    void concurrentCloseRoom_onlyOneSucceeds_andSingleNotification() throws Exception {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        List<ErrorCode> failedCodes = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        supportRoomService.closeRoom(roomId, new SupportRoomCloseRequest("종료"));
                        return true;
                    } catch (BusinessException e) {
                        failedCodes.add(e.getErrorCode());
                        return false;
                    }
                }, executor));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        long successCount = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();

        // 정확히 1건만 성공 — closeIfOpen UPDATE가 1 row만 갱신
        assertThat(successCount).isEqualTo(1);
        // 패자는 CS_002 — updated==0 분기
        assertThat(failedCodes).containsExactly(ErrorCode.SUPPORT_ROOM_ALREADY_CLOSED);
        // DB CLOSED 상태 확인
        SupportRoom room = supportRoomRepository.findById(roomId).orElseThrow();
        assertThat(room.getStatus()).isEqualTo(SupportRoomStatus.CLOSED);
        // AFTER_COMMIT 이벤트 단일 발행 → 알림 정확히 1건 생성
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
