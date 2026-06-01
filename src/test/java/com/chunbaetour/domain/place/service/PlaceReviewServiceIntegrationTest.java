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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.chunbaetour.domain.place.dto.response.PlaceReviewResponse;
import com.chunbaetour.domain.place.dto.response.UserReviewResponse;

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

        assertThatThrownBy(() -> placeReviewService.getPlaceReviews(testPlace.getId(), PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.PLACE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("관광지 리뷰 목록을 정상적으로 조회할 수 있어야 한다 (최신순)")
    void can_read_place_reviews() {
        ReviewCreateRequest request1 = new ReviewCreateRequest(5, "첫 번째 리뷰", null);
        placeReviewService.createReview(testUser.getId(), testPlace.getId(), request1);

        Account testUser2 = Account.registerUser("test2@test.com", "password", "테스터2");
        accountRepository.save(testUser2);
        ReviewCreateRequest request2 = new ReviewCreateRequest(4, "두 번째 리뷰", null);
        placeReviewService.createReview(testUser2.getId(), testPlace.getId(), request2);

        Page<PlaceReviewResponse> reviews = placeReviewService.getPlaceReviews(
                testPlace.getId(), 
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );

        assertThat(reviews.getContent()).hasSize(2);
        assertThat(reviews.getContent().get(0).content()).isEqualTo("두 번째 리뷰"); // 최신순
        assertThat(reviews.getContent().get(1).content()).isEqualTo("첫 번째 리뷰");
    }

    @Test
    @DisplayName("내가 작성한 리뷰 목록을 정상적으로 조회할 수 있어야 한다 (마이페이지용)")
    void can_read_my_reviews() {
        // testUser가 두 개의 리뷰 작성
        ReviewCreateRequest request1 = new ReviewCreateRequest(5, "내 첫 리뷰", null);
        placeReviewService.createReview(testUser.getId(), testPlace.getId(), request1);

        Place testPlace2 = Place.builder()
                .name("테스트 관광지 2")
                .address("부산")
                .lat(new BigDecimal("35.0"))
                .lng(new BigDecimal("129.0"))
                .category(PlaceCategory.TOURIST_SPOT)
                .build();
        placeRepository.save(testPlace2);

        ReviewCreateRequest request2 = new ReviewCreateRequest(4, "내 두 번째 리뷰", null);
        placeReviewService.createReview(testUser.getId(), testPlace2.getId(), request2);

        // 다른 유저가 리뷰 작성 (내 목록에 안 나와야 함)
        Account testUser2 = Account.registerUser("other@test.com", "password", "다른유저");
        accountRepository.save(testUser2);
        placeReviewService.createReview(testUser2.getId(), testPlace.getId(), new ReviewCreateRequest(3, "남의 리뷰", null));

        Page<UserReviewResponse> myReviews = 
            placeReviewService.getUserReviews(
                testUser.getId(), 
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
            );

        assertThat(myReviews.getContent()).hasSize(2);
        assertThat(myReviews.getContent().get(0).content()).isEqualTo("내 두 번째 리뷰");
        assertThat(myReviews.getContent().get(0).placeName()).isEqualTo("테스트 관광지 2");
        assertThat(myReviews.getContent().get(1).content()).isEqualTo("내 첫 리뷰");
        assertThat(myReviews.getContent().get(1).placeName()).isEqualTo("테스트 관광지");
    }

    @Test
    @DisplayName("리뷰를 작성했더라도, 이후 관광지가 삭제(비활성)되면 내 리뷰 목록에서 노출되지 않아야 한다")
    void should_not_return_reviews_for_deleted_place() {
        // 리뷰 작성
        ReviewCreateRequest request = new ReviewCreateRequest(5, "좋았던 곳", null);
        placeReviewService.createReview(testUser.getId(), testPlace.getId(), request);

        // 작성된 리뷰 확인 (1건 조회됨)
        Page<UserReviewResponse> beforeDelete = placeReviewService.getUserReviews(
                testUser.getId(), PageRequest.of(0, 10));
        assertThat(beforeDelete.getContent()).hasSize(1);

        // 관광지 삭제 처리 (soft delete)
        testPlace.delete();
        placeRepository.save(testPlace);

        // 삭제 후 다시 내 리뷰 조회 (0건이어야 함)
        Page<UserReviewResponse> afterDelete = placeReviewService.getUserReviews(
                testUser.getId(), PageRequest.of(0, 10));
        assertThat(afterDelete.getContent()).isEmpty();
    }

    @Test
    @DisplayName("마이페이지 내 리뷰 조회 시 page size가 100을 초과하면 예외가 발생해야 한다")
    void cannot_read_my_reviews_exceeding_max_page_size() {
        assertThatThrownBy(() -> placeReviewService.getUserReviews(
                testUser.getId(), 
                PageRequest.of(0, 101)
        ))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.INVALID_REQUEST.getMessage());
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
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

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
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        Place updatedPlace = placeRepository.findById(testPlace.getId()).orElseThrow();
        assertThat(updatedPlace.getReviewCount()).isEqualTo(userCount);
        assertThat(updatedPlace.getRating()).isEqualTo(5.0); // 10명이 모두 5점을 주었으므로 평균은 5.0
        assertThat(placeReviewRepository.count()).isEqualTo(userCount);
    }
}
