package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.dto.request.CreateJoinRequestRequest;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.repository.JoinRequestRepository;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.notification.service.NotificationRedisPubSubService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * JoinRequestService 분산 락 + TransactionTemplate 트랜잭션 경계 동시성 검증.
 * lock → TX commit → unlock 순서는 단위 테스트로 검증 불가 — 실제 Redis + DB 환경 필요.
 * NotificationRedisPubSubService는 동시성 검증 범위 외 — mock으로 Redis Pub/Sub 발행 차단.
 */
@SpringBootTest
class JoinRequestConcurrencyTest extends AbstractIntegrationTest {

    private static final Long OWNER_ID = 98000L;

    // 동시성 테스트 범위 외 — Redis Pub/Sub 발행을 막아 MessageListenerAdapter NPE 차단
    @MockitoBean NotificationRedisPubSubService notificationRedisPubSubService;

    @Autowired private JoinRequestService joinRequestService;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired private JoinRequestRepository joinRequestRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    // 테스트 간 격리 — 관련 엔티티 전체 삭제 + Redisson 락 키 정리
    @AfterEach
    void cleanup() {
        joinRequestRepository.deleteAll();
        chatRoomMemberRepository.deleteAll();
        chatRoomRepository.deleteAll();
        accountRepository.deleteAll();
        deleteRedisKeys("chatroom:lock:*");
    }

    // 동시 수락 → 정원 초과 발생하지 않음 (분산 락 + TransactionTemplate 경계 보장)
    @Test
    void concurrentApproveJoinRequest_doesNotExceedCapacity() throws Exception {
        // maxMembers=3, currentMembers=1(owner) → 2 슬롯 남음
        ChatRoom chatRoom = ChatRoom.createWithOwner(1L, OWNER_ID, "동시성 테스트 방", null, 3);
        chatRoomRepository.saveAndFlush(chatRoom);
        Long chatRoomId = chatRoom.getId();

        // PENDING 신청 5건 (userId 98001~98005) — Account FK 없음, chatRoomMember.userId는 plain column
        List<Long> requestIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            JoinRequest jr = JoinRequest.builder()
                    .chatRoomId(chatRoomId)
                    .userId(98001L + i)
                    .build();
            requestIds.add(joinRequestRepository.saveAndFlush(jr).getId());
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        // fixture 오류·회귀를 동시성 충돌과 구분 — 허용 코드 외 ErrorCode 검출 시 테스트 실패
        List<ErrorCode> failedCodes = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < threadCount; i++) {
                Long requestId = requestIds.get(i);
                futures.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        joinRequestService.approveJoinRequest(OWNER_ID, chatRoomId, requestId);
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
            executor.shutdownNow();
        }

        // join() 결과를 Boolean으로 먼저 추출 — 예외 완료 future가 있을 경우 filter 도중 throw 방지
        long successCount = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();
        ChatRoom updated = chatRoomRepository.findById(chatRoomId).orElseThrow();

        // 2슬롯만 남았으므로 정확히 2건 수락, 정원 초과 없음
        assertThat(successCount).isEqualTo(2);
        assertThat(updated.getCurrentMembers()).isEqualTo(3);
        assertThat(updated.getStatus()).isEqualTo(ChatRoomStatus.FULL);
        assertThat(chatRoomMemberRepository.findByChatRoomId(chatRoomId)).hasSize(3);
        // 실패 이유가 동시성 충돌·이미 처리됨·정원 초과 범위 내인지 검증
        assertThat(failedCodes).allSatisfy(code -> assertThat(code).isIn(
                ErrorCode.CHAT_APPLICATION_ALREADY_PROCESSED,
                ErrorCode.CHAT_ROOM_FULL,
                ErrorCode.CONCURRENT_UPDATE));
    }

    // 동시 중복 신청 → PENDING unique 제약으로 1건만 생성 (분산 락 직렬화 + DB 제약 이중 방어)
    @Test
    void concurrentCreateJoinRequest_duplicatePending_onlyOneSucceeds() throws Exception {
        // Account 필요 — createJoinRequest가 accountRepository.findById() 호출
        Account account = accountRepository.saveAndFlush(
                Account.registerUser("concurrent@test.com", "hashedPw", "동시테스트유저"));
        Long userId = account.getId();

        ChatRoom chatRoom = ChatRoom.createWithOwner(2L, OWNER_ID, "중복 신청 테스트 방", null, 5);
        chatRoomRepository.saveAndFlush(chatRoom);
        Long chatRoomId = chatRoom.getId();

        int threadCount = 5;
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
                        joinRequestService.createJoinRequest(userId, chatRoomId,
                                new CreateJoinRequestRequest("동시 신청"));
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
            executor.shutdownNow();
        }

        // join() 결과를 Boolean으로 먼저 추출 — 예외 완료 future가 있을 경우 filter 도중 throw 방지
        long successCount = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();

        // 동일 유저의 동시 중복 신청 → 정확히 1건만 PENDING 저장
        assertThat(successCount).isEqualTo(1);
        assertThat(joinRequestRepository.count()).isEqualTo(1);
        assertThat(failedCodes).allSatisfy(code -> assertThat(code).isIn(
                ErrorCode.ALREADY_APPLIED_CHAT,
                ErrorCode.CONCURRENT_UPDATE));
    }

    // 서로 다른 사용자 동시 신청 → 서비스 레벨 분산 락 직렬화 검증
    // createJoinRequest는 currentMembers를 변경하지 않으므로 방이 FULL이 아닌 한 다른 userId는 모두 성공 가능.
    // 같은 userId 중복 신청은 DB unique 제약(pending_key)이 차단 — 서비스 락 회귀를 놓칠 수 있어 별도 검증.
    // 이 테스트는 DB 제약 없이도 서비스 락이 유효한 요청을 차단하지 않음(false-block 없음)을 검증한다.
    @Test
    void concurrentCreateJoinRequest_differentUsers_allSucceed() throws Exception {
        // 5명 Account 저장 — createJoinRequest가 accountRepository.findById() 호출
        int threadCount = 5;
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Account account = accountRepository.saveAndFlush(
                    Account.registerUser("diffuser" + i + "@test.com", "hashedPw", "다른유저" + i));
            userIds.add(account.getId());
        }

        // maxMembers=10 — 정원 여유 충분, FULL 상태 아님
        ChatRoom chatRoom = ChatRoom.createWithOwner(3L, OWNER_ID, "다른유저 동시 신청 테스트 방", null, 10);
        chatRoomRepository.saveAndFlush(chatRoom);
        Long chatRoomId = chatRoom.getId();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        List<ErrorCode> failedCodes = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < threadCount; i++) {
                Long userId = userIds.get(i);
                futures.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        joinRequestService.createJoinRequest(userId, chatRoomId,
                                new CreateJoinRequestRequest("동시 신청"));
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
            executor.shutdownNow();
        }

        long successCount = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();

        // 다른 userId → DB unique 제약 없음에도 서비스 락이 직렬화하여 5건 모두 정상 생성
        assertThat(successCount).isEqualTo(5);
        assertThat(joinRequestRepository.count()).isEqualTo(5);
        assertThat(failedCodes).isEmpty();
    }

    // CountDownLatch.await() InterruptedException 래핑
    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // Redis 키 패턴 일괄 삭제
    private void deleteRedisKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
