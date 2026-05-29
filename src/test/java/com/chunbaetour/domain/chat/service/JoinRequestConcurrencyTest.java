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
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.ArrayList;
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

/**
 * JoinRequestService 분산 락 + TransactionTemplate 트랜잭션 경계 동시성 검증.
 * lock → TX commit → unlock 순서는 단위 테스트로 검증 불가 — 실제 Redis + DB 환경 필요.
 */
@SpringBootTest
class JoinRequestConcurrencyTest extends AbstractIntegrationTest {

    private static final Long OWNER_ID = 98000L;

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

        long successCount = futures.stream().filter(CompletableFuture::join).count();
        ChatRoom updated = chatRoomRepository.findById(chatRoomId).orElseThrow();

        // 2슬롯만 남았으므로 정확히 2건 수락, 정원 초과 없음
        assertThat(successCount).isEqualTo(2);
        assertThat(updated.getCurrentMembers()).isEqualTo(3);
        assertThat(updated.getStatus()).isEqualTo(ChatRoomStatus.FULL);
        assertThat(chatRoomMemberRepository.findByChatRoomId(chatRoomId)).hasSize(3);
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

        long successCount = futures.stream().filter(CompletableFuture::join).count();

        // 동일 유저의 동시 중복 신청 → 정확히 1건만 PENDING 저장
        assertThat(successCount).isEqualTo(1);
        assertThat(joinRequestRepository.count()).isEqualTo(1);
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
