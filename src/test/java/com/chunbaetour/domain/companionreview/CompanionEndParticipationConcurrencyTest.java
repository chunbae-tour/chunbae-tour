package com.chunbaetour.domain.companionreview;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.companionreview.entity.Companion;
import com.chunbaetour.domain.companionreview.entity.CompanionParticipant;
import com.chunbaetour.domain.companionreview.repository.CompanionParticipantRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionRepository;
import com.chunbaetour.domain.companionreview.service.CompanionService;
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
 * endParticipation()의 조건부 UPDATE(endParticipationIfNotEnded) 동시성 검증.
 * PR #531 리뷰 반영 — 동일 참여자의 동시 종료 요청 둘 다 영향 행 0/1로 갈리는지 실 DB로 확인.
 */
@SpringBootTest
class CompanionEndParticipationConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private CompanionService companionService;
    @Autowired private CompanionRepository companionRepository;
    @Autowired private CompanionParticipantRepository companionParticipantRepository;
    @Autowired private AccountRepository accountRepository;

    // FK 없는 컬럼이지만 의존 순서 유지: participant → companion → account
    @AfterEach
    void cleanup() {
        companionParticipantRepository.deleteAll();
        companionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // 동일 참여자의 동시 endParticipation 2건 → 1건만 성공, 나머지는 CR_015(ALREADY_ENDED)
    @Test
    @DisplayName("동일 참여자 동시 endParticipation 2건 → 1건만 성공, 나머지 CR_015(ALREADY_ENDED)")
    void concurrentEndParticipation_sameParticipant_onlyOneSucceeds() throws Exception {
        long chatRoomId = ThreadLocalRandom.current().nextLong(5_000_000L, 5_500_000L);

        Account user = accountRepository.saveAndFlush(
                Account.registerUser("end-participation@test.com", "hashedPw", "참여종료테스트유저"));

        // ENDED 동행 — endParticipation 호출 가능 상태
        Companion companion = Companion.builder().chatRoomId(chatRoomId)
                .tripStartDate(LocalDate.of(2026, 8, 1)).tripEndDate(LocalDate.of(2026, 8, 5)).build();
        companion.end();
        companionRepository.saveAndFlush(companion);

        companionParticipantRepository.saveAndFlush(
                CompanionParticipant.builder().companionId(companion.getId()).userId(user.getId()).build());

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
                        companionService.endParticipation(user.getId(), chatRoomId);
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
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        long successCount = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();
        CompanionParticipant updated = companionParticipantRepository.findByCompanionId(companion.getId()).get(0);

        assertThat(successCount).isEqualTo(1);
        assertThat(updated.getEndedAt()).isNotNull();
        assertThat(failedCodes).hasSize(threadCount - 1);
        assertThat(failedCodes).allSatisfy(code -> assertThat(code).isEqualTo(ErrorCode.COMPANION_PARTICIPATION_ALREADY_ENDED));
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
