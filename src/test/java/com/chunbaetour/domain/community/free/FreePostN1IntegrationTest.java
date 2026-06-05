package com.chunbaetour.domain.community.free;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class FreePostN1IntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FreePostRepository freePostRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private EntityManagerFactory emf;

    private Statistics stats;

    @BeforeEach
    void setUpStats() {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
    }

    @AfterEach
    void tearDown() {
        freePostRepository.deleteAll();
        stats.clear();
    }

    @Test
    @Transactional
    @DisplayName("findByCursor — BatchSize(100)으로 N개 게시글 이미지 배치 로드 (collection fetch = 1, N+1 아님)")
    void findByCursor_batchSize_noNPlusOne() {
        for (int i = 1; i <= 3; i++) {
            freePostRepository.save(FreePost.create(
                1L, "글" + i, "내용" + i, List.of("a" + i + ".jpg", "b" + i + ".jpg")
            ));
        }
        em.flush();
        em.clear();
        stats.clear();

        List<FreePost> posts = freePostRepository.findByCursor(
            FreePostStatus.ACTIVE, null, PageRequest.of(0, 10)
        );
        posts.forEach(p -> p.getImageUrls().size());

        // N+1이면 3, @BatchSize(100) 배치면 IN 쿼리 1개
        assertThat(stats.getCollectionFetchCount())
            .as("3개 게시글 이미지가 IN 쿼리 1개로 배치 로드되어야 함 (N+1 아님)")
            .isEqualTo(1L);
    }

    @Test
    @Transactional
    @DisplayName("findByIdWithImages — LEFT JOIN FETCH로 이미지 추가 SELECT 없음 (총 쿼리 1개)")
    void findByIdWithImages_joinFetch_noExtraSelect() {
        FreePost saved = freePostRepository.save(
            FreePost.create(1L, "제목", "내용", List.of("img1.jpg", "img2.jpg"))
        );
        em.flush();
        em.clear();
        stats.clear();

        FreePost post = freePostRepository.findByIdWithImages(saved.getId()).orElseThrow();
        post.getImageUrls().size();

        assertThat(stats.getCollectionFetchCount())
            .as("JOIN FETCH — 별도 imageUrls SELECT 없어야 함")
            .isEqualTo(0L);
        assertThat(stats.getPrepareStatementCount())
            .as("JOIN FETCH 포함 단일 SELECT 1개만 발생해야 함")
            .isEqualTo(1L);
    }

    @Test
    @Transactional
    @DisplayName("update — findByIdWithImages 로드 후 imageUrls.clear() 시 lazy SELECT 없음")
    void update_joinFetchPreload_noPlusOneOnClear() {
        FreePost saved = freePostRepository.save(
            FreePost.create(1L, "제목", "내용", List.of("old.jpg"))
        );
        em.flush();
        em.clear();

        FreePost post = freePostRepository.findByIdWithImages(saved.getId()).orElseThrow();
        stats.clear(); // JOIN FETCH 완료 후 리셋 — 이후 clear()에서 추가 fetch만 측정

        post.update("새 제목", null, List.of("new.jpg")); // imageUrls.clear() + addAll()

        assertThat(stats.getCollectionFetchCount())
            .as("이미 JOIN FETCH로 로드된 컬렉션 — clear() 시 추가 lazy SELECT 없어야 함")
            .isEqualTo(0L);
    }
}
