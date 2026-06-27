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
import com.chunbaetour.domain.companion.dto.response.CompanionCreateResponse;
import com.chunbaetour.domain.companion.entity.Companion;
import com.chunbaetour.domain.companion.entity.CompanionParticipant;
import com.chunbaetour.domain.companion.repository.CompanionParticipantRepository;
import com.chunbaetour.domain.companion.repository.CompanionRepository;
import com.chunbaetour.domain.companion.service.CompanionService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * createCompanion()의 분산 락(MultiLock) + overlap 체크(CR_010) TOCTOU 방지 검증.
 * SELECT(다른 ONGOING 동행 기간 조회) 후 INSERT(Companion/CompanionParticipant 저장) 사이의
 * race는 실제 Redis + DB 환경 필요 — 단위 테스트(mock)로는 검증 불가.
 */
@SpringBootTest
class CompanionTripOverlapConcurrencyTest extends AbstractIntegrationTest {

    private static final LocalDate TRIP_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate TRIP_END = LocalDate.of(2026, 8, 5);

    @Autowired private CompanionService companionService;
    @Autowired private CompanionRepository companionRepository;
    @Autowired private CompanionParticipantRepository companionParticipantRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    // FK 삭제 순서: participant → companion → chatRoomMember → chatRoom → account
    @AfterEach
    void cleanup() {
        companionParticipantRepository.deleteAll();
        companionRepository.deleteAll();
        chatRoomMemberRepository.deleteAll();
        chatRoomRepository.deleteAll();
        accountRepository.deleteAll();
        deleteRedisKeys("companion:trip-overlap:user:*");
    }

    // 같은 유저(owner) 1명이 겹치는 기간의 동행을 동시에 5개 방에서 생성 시도 → 분산 락으로 직렬화, 1건만 성공 + 나머지 실패
    @Test
    @DisplayName("같은 유저가 겹치는 기간으로 동시 동행 생성 5건 → 1건만 성공, 나머지 실패(CR_010 또는 락 타임아웃)")
    void concurrentCreateCompanion_sameOwnerOverlappingPeriod_onlyOneSucceeds() throws Exception {
        Account owner = accountRepository.saveAndFlush(
                Account.registerUser("trip-overlap-owner@test.com", "hashedPw", "오버랩테스트유저"));
        Account member = accountRepository.saveAndFlush(
                Account.registerUser("trip-overlap-member@test.com", "hashedPw", "오버랩테스트멤버"));
        Long ownerId = owner.getId();

        int threadCount = 5;
        List<Long> chatRoomIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            long postId = ThreadLocalRandom.current().nextLong(2_000_000L, 3_000_000L) + i;
            ChatRoom chatRoom = chatRoomRepository.saveAndFlush(
                    ChatRoom.createWithOwner(postId, ownerId, "오버랩 테스트 방" + i, null, 5));
            chatRoomMemberRepository.saveAndFlush(ChatRoomMember.ofMember(chatRoom, member.getId()));
            chatRoomIds.add(chatRoom.getId());
        }

        CompanionCreateRequest request = new CompanionCreateRequest(List.of(member.getId()), TRIP_START, TRIP_END);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        List<ErrorCode> failedCodes = Collections.synchronizedList(new ArrayList<>());

        boolean allReady;
        try {
            for (Long chatRoomId : chatRoomIds) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        companionService.createCompanion(ownerId, chatRoomId, request);
                        return true;
                    } catch (BusinessException e) {
                        failedCodes.add(e.getErrorCode());
                        return false;
                    } catch (Exception e) {
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
            // allReady == false면 워커 일부가 아직 start.await() 대기 중 — countDown 보류된 채로 shutdownNow()가 인터럽트 처리
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        long successCount = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();

        // 같은 유저(owner)의 기간이 전부 겹쳐 분산 락이 직렬화 → 1건만 성공
        // 나머지는 overlap 체크(CR_010) 또는 락 대기 타임아웃(CONCURRENT_UPDATE) — 어느 쪽이든 DB에 겹치는 동행이 남지 않음을 보장
        assertThat(successCount).isEqualTo(1);
        assertThat(companionRepository.count()).isEqualTo(1);
        assertThat(failedCodes).hasSize(threadCount - 1);
        assertThat(failedCodes).allSatisfy(code -> assertThat(code).isIn(
                ErrorCode.COMPANION_DATE_OVERLAP, ErrorCode.CONCURRENT_UPDATE));
    }

    // 동행 A={owner1,shared}, B={owner2,shared} — 겹치는 참여자(shared) 때문에 MultiLock이 직렬화, 1건만 성공
    @Test
    @DisplayName("참여자 일부 공유(겹치는 기간) 동시 동행 생성 2건 → MultiLock 직렬화로 1건만 성공, 나머지 CR_010")
    void concurrentCreateCompanion_sharedParticipantOverlappingPeriod_onlyOneSucceeds() throws Exception {
        Account owner1 = accountRepository.saveAndFlush(
                Account.registerUser("trip-overlap-owner1@test.com", "hashedPw", "오너1"));
        Account owner2 = accountRepository.saveAndFlush(
                Account.registerUser("trip-overlap-owner2@test.com", "hashedPw", "오너2"));
        Account shared = accountRepository.saveAndFlush(
                Account.registerUser("trip-overlap-shared@test.com", "hashedPw", "공유참여자"));

        long postId1 = ThreadLocalRandom.current().nextLong(3_000_000L, 3_500_000L);
        long postId2 = ThreadLocalRandom.current().nextLong(3_500_000L, 4_000_000L);

        ChatRoom chatRoomA = ChatRoom.createWithOwner(postId1, owner1.getId(), "오버랩 테스트 방A", null, 5);
        chatRoomRepository.saveAndFlush(chatRoomA);
        chatRoomMemberRepository.saveAndFlush(ChatRoomMember.ofMember(chatRoomA, shared.getId()));

        ChatRoom chatRoomB = ChatRoom.createWithOwner(postId2, owner2.getId(), "오버랩 테스트 방B", null, 5);
        chatRoomRepository.saveAndFlush(chatRoomB);
        chatRoomMemberRepository.saveAndFlush(ChatRoomMember.ofMember(chatRoomB, shared.getId()));

        CompanionCreateRequest requestA = new CompanionCreateRequest(List.of(shared.getId()), TRIP_START, TRIP_END);
        CompanionCreateRequest requestB = new CompanionCreateRequest(List.of(shared.getId()), TRIP_START, TRIP_END);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<ErrorCode> failedCodes = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture<Boolean> futureA = CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            await(start);
            try {
                companionService.createCompanion(owner1.getId(), chatRoomA.getId(), requestA);
                return true;
            } catch (BusinessException e) {
                failedCodes.add(e.getErrorCode());
                return false;
            } catch (Exception e) {
                return false;
            }
        }, executor);

        CompletableFuture<Boolean> futureB = CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            await(start);
            try {
                companionService.createCompanion(owner2.getId(), chatRoomB.getId(), requestB);
                return true;
            } catch (BusinessException e) {
                failedCodes.add(e.getErrorCode());
                return false;
            } catch (Exception e) {
                return false;
            }
        }, executor);

        try {
            boolean allReady = ready.await(5, TimeUnit.SECONDS);
            if (allReady) {
                start.countDown();
            }
            assertThat(allReady).isTrue();
            CompletableFuture.allOf(futureA, futureB).get(30, TimeUnit.SECONDS);
        } finally {
            // allReady == false면 워커 일부가 아직 start.await() 대기 중 — countDown 보류된 채로 shutdownNow()가 인터럽트 처리
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        long successCount = (futureA.join() ? 1 : 0) + (futureB.join() ? 1 : 0);

        // shared 유저 락이 공유되어 직렬화 → 1건만 성공, 나머지는 CR_010 또는 락 대기 타임아웃
        assertThat(successCount).isEqualTo(1);
        assertThat(companionRepository.count()).isEqualTo(1);
        assertThat(failedCodes).hasSize(1);
        assertThat(failedCodes).allSatisfy(code -> assertThat(code).isIn(
                ErrorCode.COMPANION_DATE_OVERLAP, ErrorCode.CONCURRENT_UPDATE));
    }

    // 무관한 유저(X)가 겹치는 기간의 ONGOING 동행을 갖고 있어도, X를 참여자로 포함하지 않는 신규 동행 생성은 성공 — IN :userIds 스코핑(CR_010) 검증
    @Test
    @DisplayName("무관한 유저의 겹치는 ONGOING 동행이 있어도 해당 유저 미포함 신규 동행 생성은 성공 (IN :userIds 스코핑)")
    void createCompanion_overlappingPeriodOfUnrelatedUser_succeeds() {
        Account unrelatedUser = accountRepository.saveAndFlush(
                Account.registerUser("trip-overlap-unrelated@test.com", "hashedPw", "무관유저"));
        Account owner = accountRepository.saveAndFlush(
                Account.registerUser("trip-overlap-owner2@test.com", "hashedPw", "오버랩테스트유저2"));

        long unrelatedPostId = ThreadLocalRandom.current().nextLong(4_000_000L, 4_500_000L);
        ChatRoom unrelatedRoom = chatRoomRepository.saveAndFlush(
                ChatRoom.createWithOwner(unrelatedPostId, unrelatedUser.getId(), "무관 유저 동행방", null, 5));

        // 무관한 유저의 ONGOING 동행 — owner가 생성할 기간과 동일하게 겹침
        Companion unrelatedCompanion = companionRepository.saveAndFlush(
                Companion.builder().chatRoomId(unrelatedRoom.getId()).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build());
        companionParticipantRepository.saveAndFlush(
                CompanionParticipant.builder().companionId(unrelatedCompanion.getId()).userId(unrelatedUser.getId()).build());

        long ownerPostId = ThreadLocalRandom.current().nextLong(4_500_000L, 5_000_000L);
        ChatRoom ownerRoom = chatRoomRepository.saveAndFlush(
                ChatRoom.createWithOwner(ownerPostId, owner.getId(), "오버랩 테스트 방2", null, 5));
        Account ownerMember = accountRepository.saveAndFlush(
                Account.registerUser("trip-overlap-owner2-member@test.com", "hashedPw", "무관유저방멤버"));
        chatRoomMemberRepository.saveAndFlush(ChatRoomMember.ofMember(ownerRoom, ownerMember.getId()));

        CompanionCreateResponse response = companionService.createCompanion(
                owner.getId(), ownerRoom.getId(), new CompanionCreateRequest(List.of(ownerMember.getId()), TRIP_START, TRIP_END));

        assertThat(response).isNotNull();
        assertThat(companionRepository.count()).isEqualTo(2);
    }

    // 같은 유저의 다른 방 ENDED 동행이 겹치는 기간이어도 신규 동행 생성은 성공 — status=ONGOING 필터(CR_010) 검증
    @Test
    @DisplayName("같은 유저의 다른 방 ENDED 동행이 겹치는 기간이어도 신규 동행 생성은 성공 (status=ONGOING 필터)")
    void createCompanion_overlappingPeriodOfEndedCompanion_succeeds() {
        Account owner = accountRepository.saveAndFlush(
                Account.registerUser("trip-overlap-owner3@test.com", "hashedPw", "오버랩테스트유저3"));

        long endedPostId = ThreadLocalRandom.current().nextLong(5_000_000L, 5_500_000L);
        ChatRoom endedRoom = chatRoomRepository.saveAndFlush(
                ChatRoom.createWithOwner(endedPostId, owner.getId(), "종료된 동행방", null, 5));

        // 같은 유저의 ENDED 동행 — 신규 생성할 기간과 동일하게 겹침
        Companion endedCompanion = Companion.builder()
                .chatRoomId(endedRoom.getId()).tripStartDate(TRIP_START).tripEndDate(TRIP_END).build();
        endedCompanion.end();
        companionRepository.saveAndFlush(endedCompanion);
        companionParticipantRepository.saveAndFlush(
                CompanionParticipant.builder().companionId(endedCompanion.getId()).userId(owner.getId()).build());

        long newPostId = ThreadLocalRandom.current().nextLong(5_500_000L, 6_000_000L);
        ChatRoom newRoom = chatRoomRepository.saveAndFlush(
                ChatRoom.createWithOwner(newPostId, owner.getId(), "오버랩 테스트 방3", null, 5));
        Account newMember = accountRepository.saveAndFlush(
                Account.registerUser("trip-overlap-owner3-member@test.com", "hashedPw", "종료방멤버"));
        chatRoomMemberRepository.saveAndFlush(ChatRoomMember.ofMember(newRoom, newMember.getId()));

        CompanionCreateResponse response = companionService.createCompanion(
                owner.getId(), newRoom.getId(), new CompanionCreateRequest(List.of(newMember.getId()), TRIP_START, TRIP_END));

        assertThat(response).isNotNull();
        assertThat(companionRepository.count()).isEqualTo(2);
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
