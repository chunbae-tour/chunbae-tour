package com.chunbaetour.domain.companionreview;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.companionreview.dto.request.CompanionReviewCreateRequest;
import com.chunbaetour.domain.companionreview.entity.Companion;
import com.chunbaetour.domain.companionreview.entity.CompanionParticipant;
import com.chunbaetour.domain.companionreview.repository.CompanionParticipantRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionRepository;
import com.chunbaetour.domain.companionreview.repository.CompanionReviewRepository;
import com.chunbaetour.domain.companionreview.service.CompanionReviewService;
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
 * createReview()의 PESSIMISTIC_WRITE(target Account findByIdWithLock) +
 * uq_companion_review(reviewer_id, target_user_id, chat_room_id) 동시성 검증.
 * existsBy 선검사는 락 이전이라 race로 통과될 수 있음 — 최종 방어선은 DB unique 제약 + DataIntegrityViolationException catch.
 */
@SpringBootTest
class CompanionReviewConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private CompanionReviewService companionReviewService;
    @Autowired private CompanionRepository companionRepository;
    @Autowired private CompanionParticipantRepository companionParticipantRepository;
    @Autowired private CompanionReviewRepository companionReviewRepository;
    @Autowired private AccountRepository accountRepository;

    // FK 없는 컬럼이지만 의존 순서 유지: review → participant → companion → account
    @AfterEach
    void cleanup() {
        companionReviewRepository.deleteAll();
        companionParticipantRepository.deleteAll();
        companionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // 동일 (reviewer, target, chatRoom) 동시 리뷰 10건 → 1건만 성공, 나머지 CR_001 — existsBy race + uq_companion_review 최종 방어 검증
    @Test
    @DisplayName("동일 (reviewer, target, chatRoom) 동시 리뷰 10건 → 1건만 성공, 나머지 CR_001(ALREADY_EXISTS)")
    void concurrentCreateReview_sameTarget_onlyOneSucceeds() throws Exception {
        // 다른 테스트와의 chat_room_id UNIQUE 제약(uq_companions_chat_room_id) 충돌 방지용 랜덤 ID
        long chatRoomId = ThreadLocalRandom.current().nextLong(1_000_000L, 2_000_000L);

        Account reviewer = accountRepository.saveAndFlush(
                Account.registerUser("cr-reviewer@test.com", "hashedPw", "리뷰어"));
        Account target = accountRepository.saveAndFlush(
                Account.registerUser("cr-target@test.com", "hashedPw", "대상자"));

        // ENDED 동행 — 리뷰 작성 가능 상태
        Companion companion = Companion.builder().chatRoomId(chatRoomId)
                .tripStartDate(LocalDate.of(2026, 7, 1)).tripEndDate(LocalDate.of(2026, 7, 5)).build();
        companion.end();
        companionRepository.saveAndFlush(companion);

        companionParticipantRepository.saveAndFlush(
                CompanionParticipant.builder().companionId(companion.getId()).userId(reviewer.getId()).build());
        companionParticipantRepository.saveAndFlush(
                CompanionParticipant.builder().companionId(companion.getId()).userId(target.getId()).build());

        CompanionReviewCreateRequest request = new CompanionReviewCreateRequest(chatRoomId, target.getId(), 5, "동시성 테스트 리뷰");

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        List<ErrorCode> failedCodes = Collections.synchronizedList(new ArrayList<>());

        boolean isReady = false;
        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        companionReviewService.createReview(reviewer.getId(), request);
                        return true;
                    } catch (BusinessException e) {
                        failedCodes.add(e.getErrorCode());
                        return false;
                    } catch (Exception e) {
                        return false;
                    }
                }, executor));
            }
            isReady = ready.await(5, TimeUnit.SECONDS);
            assertThat(isReady).isTrue();
            start.countDown();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(15, TimeUnit.SECONDS);
        } finally {
            // ready 통과 못 했으면 start 미해제 — 대기 중이던 풀 스레드가 뒤늦게 createReview() 실행해 DB 오염하는 것 방지
            if (isReady) {
                start.countDown();
            }
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }

        long successCount = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();
        Account updatedTarget = accountRepository.findById(target.getId()).orElseThrow();

        // 1건만 성공, DB에도 1행만 존재
        assertThat(successCount).isEqualTo(1);
        assertThat(companionReviewRepository.count()).isEqualTo(1);
        // 점수 최종값 — 단 1회 반영 (5점 1건 평균 = 5.0)
        assertThat(updatedTarget.getCompanionReviewCount()).isEqualTo(1);
        assertThat(updatedTarget.getCompanionScore()).isEqualTo(5.0);
        // 나머지 9건은 모두 CR_001 — existsBy 선검사 또는 uq_companion_review 위반(DataIntegrityViolation catch) 경유
        assertThat(failedCodes).hasSize(threadCount - 1);
        assertThat(failedCodes).allSatisfy(code -> assertThat(code).isEqualTo(ErrorCode.COMPANION_REVIEW_ALREADY_EXISTS));
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
