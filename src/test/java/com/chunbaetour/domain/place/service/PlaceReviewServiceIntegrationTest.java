package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.dto.request.ReviewCreateRequest;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.repository.PlaceReviewRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PlaceReviewServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlaceReviewService placeReviewService;

    @Autowired
    private PlaceReviewRepository placeReviewRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Place testPlace;
    private Account testUser;

    @BeforeEach
    void setUp() {
        testUser = Account.registerUser("test@test.com", "password", "테스터");
        accountRepository.save(testUser);

        testPlace = Place.builder()
                .name("테스트 관광지")
                .address("서울")
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("127.0"))
                .category(PlaceCategory.TOURIST_SPOT)
                .status(PlaceStatus.ACTIVE)
                .build();
        placeRepository.save(testPlace);
    }

    @AfterEach
    void tearDown() {
        placeReviewRepository.deleteAllInBatch();
        placeRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("숨김/삭제 처리된 관광지에는 리뷰 작성 및 조회가 차단되어야 한다")
    void cannot_review_or_read_inactive_place() {
        testPlace.delete(); // status = DELETED
        placeRepository.save(testPlace);

        ReviewCreateRequest request = new ReviewCreateRequest(5, "좋아요", null);

        assertThatThrownBy(() -> placeReviewService.createReview(testUser.getId(), testPlace.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.PLACE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("동일 유저가 동시에 여러 번 리뷰 작성 요청 시 1번만 성공해야 한다 (동시성 중복 방어)")
    void concurrent_duplicate_reviews_by_same_user_should_fail() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        ReviewCreateRequest request = new ReviewCreateRequest(5, "테스트 리뷰", null);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    placeReviewService.createReview(testUser.getId(), testPlace.getId(), request);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.REVIEW_ALREADY_EXISTS) {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
        assertThat(placeReviewRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("다수의 유저가 동시에 리뷰를 작성해도 Place의 rating과 reviewCount에 Lost Update가 발생하지 않아야 한다")
    void concurrent_reviews_by_different_users_should_update_place_stats_safely() throws InterruptedException {
        int userCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);

        // 10명의 유저 생성
        for (int i = 0; i < userCount; i++) {
            Account u = Account.registerUser("test" + i + "@test.com", "password", "유저" + i);
            accountRepository.save(u);

            ReviewCreateRequest request = new ReviewCreateRequest(5, "최고예요 " + i, List.of("url"));
            executorService.submit(() -> {
                try {
                    placeReviewService.createReview(u.getId(), testPlace.getId(), request);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        Place updatedPlace = placeRepository.findById(testPlace.getId()).orElseThrow();
        assertThat(updatedPlace.getReviewCount()).isEqualTo(userCount);
        assertThat(updatedPlace.getRating()).isEqualTo(5.0); // 10명이 모두 5점을 주었으므로 평균은 5.0
        assertThat(placeReviewRepository.count()).isEqualTo(userCount);
    }
}
