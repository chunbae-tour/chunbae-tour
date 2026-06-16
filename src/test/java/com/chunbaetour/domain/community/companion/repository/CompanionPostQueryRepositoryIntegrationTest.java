package com.chunbaetour.domain.community.companion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CompanionPostQueryRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CompanionPostQueryRepository queryRepository;
    @Autowired private CompanionPostRepository postRepository;

    private static final LocalDate D1 = LocalDate.of(2026, 8, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 8, 2);

    @AfterEach
    void tearDown() {
        postRepository.deleteAll();
    }

    private CompanionPost save(String region, LocalDate date, CompanionPostStatus status) {
        CompanionPost post = CompanionPost.create(1L, "제목", "내용", 100L, "장소", region, date, 4);
        if (status == CompanionPostStatus.HIDDEN) post.hide();
        if (status == CompanionPostStatus.DELETED) post.delete();
        return postRepository.save(post);
    }

    @Test
    @DisplayName("필터 없음 — ACTIVE 전체를 id DESC로 반환")
    void noFilter_allActiveDesc() {
        save("서울", D1, CompanionPostStatus.ACTIVE);
        save("부산", D2, CompanionPostStatus.ACTIVE);
        save("서울", D1, CompanionPostStatus.HIDDEN); // 제외

        List<CompanionPost> result = queryRepository.findByFilters(
                CompanionPostStatus.ACTIVE, null, null, null, 10);

        assertThat(result).hasSize(2);
        // id DESC
        assertThat(result.get(0).getId()).isGreaterThan(result.get(1).getId());
    }

    @Test
    @DisplayName("region 필터 — 해당 지역 ACTIVE만")
    void regionFilter() {
        save("서울", D1, CompanionPostStatus.ACTIVE);
        save("부산", D1, CompanionPostStatus.ACTIVE);
        save("서울", D2, CompanionPostStatus.ACTIVE);

        List<CompanionPost> result = queryRepository.findByFilters(
                CompanionPostStatus.ACTIVE, "서울", null, null, 10);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(p -> p.getRegion().equals("서울"));
    }

    @Test
    @DisplayName("meetingDate 필터 — 해당 날짜 ACTIVE만")
    void meetingDateFilter() {
        save("서울", D1, CompanionPostStatus.ACTIVE);
        save("부산", D2, CompanionPostStatus.ACTIVE);

        List<CompanionPost> result = queryRepository.findByFilters(
                CompanionPostStatus.ACTIVE, null, D2, null, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMeetingDate()).isEqualTo(D2);
    }

    @Test
    @DisplayName("region + meetingDate 동시 필터")
    void regionAndDateFilter() {
        save("서울", D1, CompanionPostStatus.ACTIVE);
        save("서울", D2, CompanionPostStatus.ACTIVE);
        save("부산", D2, CompanionPostStatus.ACTIVE);

        List<CompanionPost> result = queryRepository.findByFilters(
                CompanionPostStatus.ACTIVE, "서울", D2, null, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRegion()).isEqualTo("서울");
        assertThat(result.get(0).getMeetingDate()).isEqualTo(D2);
    }

    @Test
    @DisplayName("cursor — cursorId 미만 id만 반환")
    void cursorPaging() {
        CompanionPost p1 = save("서울", D1, CompanionPostStatus.ACTIVE);
        CompanionPost p2 = save("서울", D1, CompanionPostStatus.ACTIVE);
        CompanionPost p3 = save("서울", D1, CompanionPostStatus.ACTIVE);

        List<CompanionPost> result = queryRepository.findByFilters(
                CompanionPostStatus.ACTIVE, null, null, p3.getId(), 10);

        // p3 미만 → p1, p2
        assertThat(result).extracting(CompanionPost::getId)
                .containsExactly(p2.getId(), p1.getId());
    }

    @Test
    @DisplayName("limit — limit 개수만큼만 반환")
    void limitApplied() {
        for (int i = 0; i < 5; i++) save("서울", D1, CompanionPostStatus.ACTIVE);

        List<CompanionPost> result = queryRepository.findByFilters(
                CompanionPostStatus.ACTIVE, null, null, null, 3);

        assertThat(result).hasSize(3);
    }
}
