package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.notification.entity.Notification;
import com.chunbaetour.domain.notification.repository.NotificationRepository;
import com.chunbaetour.domain.notification.service.NotificationRedisPubSubService;
import com.chunbaetour.domain.notification.type.NotificationType;
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
 * transferOwner() @Version 낙관적 락 동시성 검증 — 같은 방에서 동시 위임 요청 시 1건만 성공.
 * NotificationRedisPubSubService는 동시성 검증 범위 외 — mock으로 Redis Pub/Sub 발행 차단.
 */
@SpringBootTest
class ChatRoomOwnerTransferConcurrencyTest extends AbstractIntegrationTest {

    private static final Long OWNER_ID = 99000L;
    private static final Long NEW_OWNER_ID = 99001L;
    private static final Long POST_ID = 1L;

    // 동시성 테스트 범위 외 — Redis Pub/Sub 발행이 실제 리스너로 전파되지 않도록 차단
    @MockitoBean NotificationRedisPubSubService notificationRedisPubSubService;

    @Autowired private ChatRoomService chatRoomService;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    // FK 삭제 순서: chatRoomMember → chatRoom
    // transferOwner 성공 시 AFTER_COMMIT으로 신규 방장에게 알림이 실제 저장되므로 함께 정리
    @AfterEach
    void cleanup() {
        notificationRepository.deleteAll();
        chatRoomMemberRepository.deleteAll();
        chatRoomRepository.deleteAll();
    }

    @Test
    @DisplayName("동일 방장 위임 2건 동시 요청 → 1건만 성공, 나머지는 CONCURRENT_UPDATE")
    void concurrentTransferOwner_onlyOneSucceeds() throws Exception {
        ChatRoom chatRoom = ChatRoom.createWithOwner(POST_ID, OWNER_ID, "방장 위임 동시성 테스트 방", null, 5);
        chatRoomRepository.saveAndFlush(chatRoom);
        Long chatRoomId = chatRoom.getId();

        // 위임 대상 — MEMBER_ACTIVE로 참여
        ChatRoomMember newOwnerMember = ChatRoomMember.ofMember(chatRoom, NEW_OWNER_ID);
        chatRoomMemberRepository.saveAndFlush(newOwnerMember);

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
                        chatRoomService.transferOwner(OWNER_ID, chatRoomId, NEW_OWNER_ID);
                        return true;
                    } catch (BusinessException e) {
                        failedCodes.add(e.getErrorCode());
                        return false;
                    } catch (Exception e) {
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

        // @Version 충돌로 한 트랜잭션만 커밋 — 나머지는 CONCURRENT_UPDATE
        assertThat(successCount).isEqualTo(1);
        assertThat(failedCodes).containsExactly(ErrorCode.CONCURRENT_UPDATE);

        // 최종 상태 — ownerId는 신규 방장으로 교체, OWNER_ACTIVE 멤버는 정확히 1명이며 ownerId와 일치
        ChatRoom updated = chatRoomRepository.findById(chatRoomId).orElseThrow();
        assertThat(updated.getOwnerId()).isEqualTo(NEW_OWNER_ID);

        List<ChatRoomMember> members = chatRoomMemberRepository.findByChatRoomId(chatRoomId);
        List<ChatRoomMember> owners = members.stream()
                .filter(m -> m.getMemberState() == ChatMemberState.OWNER_ACTIVE)
                .toList();
        assertThat(owners).hasSize(1);
        assertThat(owners.get(0).getUserId()).isEqualTo(updated.getOwnerId());

        // 위임에 실패한 트랜잭션은 CONCURRENT_UPDATE로 롤백 — 기존 방장은 MEMBER_ACTIVE로 정상 전환
        ChatRoomMember oldOwner = members.stream()
                .filter(m -> m.getUserId().equals(OWNER_ID))
                .findFirst()
                .orElseThrow();
        assertThat(oldOwner.getMemberState()).isEqualTo(ChatMemberState.MEMBER_ACTIVE);

        // 성공한 트랜잭션 1건만 AFTER_COMMIT으로 신규 방장에게 CHAT_OWNER_TRANSFERRED 알림 발송
        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getUserId()).isEqualTo(NEW_OWNER_ID);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.CHAT_OWNER_TRANSFERRED);
    }

    @Test
    @DisplayName("방장 위임과 강퇴가 같은 대상에게 동시 요청 → 1건만 성공, 데이터 정합성 유지")
    void concurrentTransferAndKick_onlyOneSucceeds_consistentState() throws Exception {
        ChatRoom chatRoom = ChatRoom.createWithOwner(POST_ID, OWNER_ID, "위임/강퇴 동시성 테스트 방", null, 5);
        chatRoomRepository.saveAndFlush(chatRoom);
        Long chatRoomId = chatRoom.getId();

        // 위임 대상 = 강퇴 대상 — MEMBER_ACTIVE로 참여
        ChatRoomMember targetMember = ChatRoomMember.ofMember(chatRoom, NEW_OWNER_ID);
        chatRoomMemberRepository.saveAndFlush(targetMember);
        chatRoom.incrementMembers();
        chatRoomRepository.saveAndFlush(chatRoom);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<ErrorCode> failedCodes = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture<Boolean> transferFuture;
        CompletableFuture<Boolean> kickFuture;
        try {
            transferFuture = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                try {
                    chatRoomService.transferOwner(OWNER_ID, chatRoomId, NEW_OWNER_ID);
                    return true;
                } catch (BusinessException e) {
                    failedCodes.add(e.getErrorCode());
                    return false;
                }
            }, executor);

            kickFuture = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                try {
                    chatRoomService.kickMember(OWNER_ID, chatRoomId, NEW_OWNER_ID);
                    return true;
                } catch (BusinessException e) {
                    failedCodes.add(e.getErrorCode());
                    return false;
                }
            }, executor);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            CompletableFuture.allOf(transferFuture, kickFuture).get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        boolean transferSucceeded = transferFuture.join();
        boolean kickSucceeded = kickFuture.join();

        // ChatRoomMember에는 @Version 없음 — ChatRoom의 @Version + 트랜잭션 롤백으로 한쪽만 커밋되어야 함
        assertThat(transferSucceeded ^ kickSucceeded).isTrue();
        assertThat(failedCodes).hasSize(1);
        assertThat(failedCodes.get(0)).isIn(ErrorCode.CONCURRENT_UPDATE, ErrorCode.CHAT_SETTING_FORBIDDEN);

        ChatRoom updated = chatRoomRepository.findById(chatRoomId).orElseThrow();
        List<ChatRoomMember> members = chatRoomMemberRepository.findByChatRoomId(chatRoomId);
        ChatRoomMember target = members.stream()
                .filter(m -> m.getUserId().equals(NEW_OWNER_ID))
                .findFirst()
                .orElseThrow();

        if (transferSucceeded) {
            // 위임 성공 — 대상이 신규 방장, 인원수는 그대로
            assertThat(updated.getOwnerId()).isEqualTo(NEW_OWNER_ID);
            assertThat(target.getMemberState()).isEqualTo(ChatMemberState.OWNER_ACTIVE);
            assertThat(updated.getCurrentMembers()).isEqualTo(2);
        } else {
            // 강퇴 성공 — 대상은 MEMBER_KICKED, 인원수 -1, ownerId는 그대로
            assertThat(updated.getOwnerId()).isEqualTo(OWNER_ID);
            assertThat(target.getMemberState()).isEqualTo(ChatMemberState.MEMBER_KICKED);
            assertThat(updated.getCurrentMembers()).isEqualTo(1);
        }

        // 어느 쪽이 성공하든 OWNER_ACTIVE 멤버는 정확히 1명이며 ownerId와 일치 — 정합성 깨짐 없음
        List<ChatRoomMember> owners = members.stream()
                .filter(m -> m.getMemberState() == ChatMemberState.OWNER_ACTIVE)
                .toList();
        assertThat(owners).hasSize(1);
        assertThat(owners.get(0).getUserId()).isEqualTo(updated.getOwnerId());
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
}
