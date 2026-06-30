package com.chunbaetour.domain.companion;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.companion.dto.request.CompanionCreateRequest;
import com.chunbaetour.domain.companion.repository.CompanionParticipantRepository;
import com.chunbaetour.domain.companion.repository.CompanionRepository;
import com.chunbaetour.domain.companion.service.CompanionService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * cancelCompanion()의 PESSIMISTIC_WRITE 동시성 + 하드 삭제 후 uq_companions_chat_room_id 해제 검증.
 * PR #521 리뷰 반영 — 이중 취소 동시성, 취소 후 재생성 시 DB unique 제약 해제를 실 DB로 확인.
 */
@SpringBootTest
class CompanionCancelConcurrencyTest extends AbstractIntegrationTest {

    private static final LocalDate TRIP_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate TRIP_END = LocalDate.of(2026, 9, 5);

    @Autowired private CompanionService companionService;
    @Autowired private CompanionRepository companionRepository;
    @Autowired private CompanionParticipantRepository companionParticipantRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired private AccountRepository accountRepository;

    // FK 삭제 순서: participant → companion → chatRoomMember → chatRoom → account
    @AfterEach
    void cleanup() {
        companionParticipantRepository.deleteAll();
        companionRepository.deleteAll();
        chatRoomMemberRepository.deleteAll();
        chatRoomRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // 동일 동행에 대한 동시 취소 요청 2건 → PESSIMISTIC_WRITE로 직렬화, 1건만 성공하고 나머지는 COMPANION_NOT_FOUND(하드 삭제 후 재조회 실패)
    @Test
    @DisplayName("동일 동행 동시 취소 2건 → 1건만 성공, 나머지 CR_005(NOT_FOUND)")
    void concurrentCancelCompanion_sameCompanion_onlyOneSucceeds() throws Exception {
        Account owner = accountRepository.saveAndFlush(
                Account.registerUser("cancel-owner@test.com", "hashedPw", "취소테스트유저"));
        Account member = accountRepository.saveAndFlush(
                Account.registerUser("cancel-member@test.com", "hashedPw", "취소테스트멤버"));
        long postId = ThreadLocalRandom.current().nextLong(4_000_000L, 4_500_000L);
        ChatRoom chatRoom = chatRoomRepository.saveAndFlush(
                ChatRoom.createWithOwner(postId, owner.getId(), "취소 동시성 테스트 방", null, 5));
        Long roomId = chatRoom.getId();
        chatRoomMemberRepository.saveAndFlush(ChatRoomMember.ofMember(chatRoom, member.getId()));

        companionService.createCompanion(owner.getId(), roomId, new CompanionCreateRequest(List.of(member.getId()), TRIP_START, TRIP_END));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        List<ErrorCode> failedCodes = Collections.synchronizedList(new ArrayList<>());

        boolean allReady;
        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        companionService.cancelCompanion(owner.getId(), roomId);
                        return true;
                    } catch (BusinessException e) {
                        failedCodes.add(e.getErrorCode());
                        return false;
                    }
                }, executor));
            }
            allReady = ready.await(5, TimeUnit.SECONDS);
            if (allReady) {
                start.countDown();
            }
            assertThat(allReady).isTrue();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        long successCount = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(companionRepository.count()).isEqualTo(0);
        assertThat(companionParticipantRepository.count()).isEqualTo(0);
        assertThat(failedCodes).hasSize(threadCount - 1);
        assertThat(failedCodes).allSatisfy(code -> assertThat(code).isEqualTo(ErrorCode.COMPANION_NOT_FOUND));
    }

    // 취소(하드 삭제) 후 같은 채팅방에서 동행 재생성 → uq_companions_chat_room_id 제약이 실제로 해제됨을 DB로 검증
    @Test
    @DisplayName("동행 취소 후 같은 채팅방에서 재생성 → uq_companions_chat_room_id 해제 확인")
    void cancelCompanion_thenCreateCompanion_dbConstraintReleased() {
        Account owner = accountRepository.saveAndFlush(
                Account.registerUser("cancel-recreate-owner@test.com", "hashedPw", "재생성테스트유저"));
        Account member = accountRepository.saveAndFlush(
                Account.registerUser("cancel-recreate-member@test.com", "hashedPw", "재생성테스트멤버"));
        long postId = ThreadLocalRandom.current().nextLong(4_500_000L, 5_000_000L);
        ChatRoom chatRoom = chatRoomRepository.saveAndFlush(
                ChatRoom.createWithOwner(postId, owner.getId(), "취소 재생성 테스트 방", null, 5));
        Long roomId = chatRoom.getId();
        chatRoomMemberRepository.saveAndFlush(ChatRoomMember.ofMember(chatRoom, member.getId()));

        var firstResponse = companionService.createCompanion(owner.getId(), roomId, new CompanionCreateRequest(List.of(member.getId()), TRIP_START, TRIP_END));
        companionService.cancelCompanion(owner.getId(), roomId);

        assertThat(companionRepository.count()).isEqualTo(0);
        assertThat(companionParticipantRepository.count()).isEqualTo(0);

        var secondResponse = companionService.createCompanion(owner.getId(), roomId, new CompanionCreateRequest(List.of(member.getId()), TRIP_START, TRIP_END));

        assertThat(secondResponse.companionId()).isNotEqualTo(firstResponse.companionId());
        assertThat(secondResponse.status()).isEqualTo("ONGOING");
        assertThat(companionRepository.count()).isEqualTo(1);
        assertThat(companionRepository.findByChatRoomId(roomId)).isPresent();
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
