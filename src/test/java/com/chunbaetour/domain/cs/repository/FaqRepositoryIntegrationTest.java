package com.chunbaetour.domain.cs.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.cs.entity.Faq;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

@SpringBootTest
class FaqRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FaqRepository faqRepository;

    // Faq는 현재 독립 엔티티(FK 없음) — 단순 deleteAll 가능
    // 향후 FaqCategory 등 연관 엔티티 추가 시 FK 삭제 순서 반드시 고려할 것
    @AfterEach
    void tearDown() {
        faqRepository.deleteAll();
    }

    // ===== findWithCursor (Admin 커서 페이징) =====

    // 활성/비활성 모두 포함, id ASC 정렬
    @Test
    void findWithCursor_returnsAllFaqsInIdAscOrder() {
        faqRepository.saveAll(List.of(
                buildFaq("PAYMENT", true),
                buildFaq("PAYMENT", false),
                buildFaq("ACCOUNT", true)
        ));

        List<Faq> result = faqRepository.findWithCursor(null, PageRequest.of(0, 10));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isLessThan(result.get(1).getId());
    }

    // cursorId 이후 id만 반환
    @Test
    void findWithCursor_returnsIdsAfterCursor() {
        List<Faq> saved = faqRepository.saveAll(List.of(
                buildFaq("PAYMENT", true),
                buildFaq("PAYMENT", true),
                buildFaq("PAYMENT", true)
        ));
        Long cursorId = saved.get(0).getId();

        List<Faq> result = faqRepository.findWithCursor(cursorId, PageRequest.of(0, 10));

        assertThat(result).allMatch(f -> f.getId() > cursorId);
    }

    // Pageable size 제한 적용
    @Test
    void findWithCursor_respectsPageableLimit() {
        for (int i = 0; i < 5; i++) {
            faqRepository.save(buildFaq("PAYMENT", true));
        }

        List<Faq> result = faqRepository.findWithCursor(null, PageRequest.of(0, 3));

        assertThat(result).hasSize(3);
    }

    // ===== findByIsActiveTrueWithCursor (User 전체 활성 FAQ 커서 페이징) =====

    // 비활성(isActive=false) FAQ는 제외됨
    @Test
    void findByIsActiveTrueWithCursor_excludesInactiveFaqs() {
        faqRepository.saveAll(List.of(
                buildFaq("PAYMENT", true),
                buildFaq("PAYMENT", false),
                buildFaq("ACCOUNT", true)
        ));

        List<Faq> result = faqRepository.findByIsActiveTrueWithCursor(null, PageRequest.of(0, 10));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(Faq::isActive);
    }

    // cursorId 이후 활성 FAQ만 반환
    @Test
    void findByIsActiveTrueWithCursor_returnsIdsAfterCursor() {
        List<Faq> saved = faqRepository.saveAll(List.of(
                buildFaq("PAYMENT", true),
                buildFaq("ACCOUNT", true),
                buildFaq("SHIPPING", true)
        ));
        Long cursorId = saved.get(0).getId();

        List<Faq> result = faqRepository.findByIsActiveTrueWithCursor(cursorId, PageRequest.of(0, 10));

        assertThat(result).allMatch(f -> f.getId() > cursorId);
        assertThat(result).hasSize(2);
    }

    // ===== findByCategoryAndIsActiveTrueWithCursor (User 카테고리별 활성 FAQ 커서 페이징) =====

    // 지정 카테고리의 활성 FAQ만 반환 — 비활성·다른 카테고리 제외
    @Test
    void findByCategoryAndIsActiveTrueWithCursor_returnsOnlyActiveFaqsInCategory() {
        faqRepository.saveAll(List.of(
                buildFaq("PAYMENT", true),
                buildFaq("PAYMENT", false),
                buildFaq("ACCOUNT", true)
        ));

        List<Faq> result = faqRepository.findByCategoryAndIsActiveTrueWithCursor("PAYMENT", null, PageRequest.of(0, 10));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("PAYMENT");
        assertThat(result.get(0).isActive()).isTrue();
    }

    // cursorId 이후 해당 카테고리 활성 FAQ만 반환
    @Test
    void findByCategoryAndIsActiveTrueWithCursor_returnsIdsAfterCursor() {
        List<Faq> saved = faqRepository.saveAll(List.of(
                buildFaq("PAYMENT", true),
                buildFaq("PAYMENT", true),
                buildFaq("PAYMENT", true)
        ));
        Long cursorId = saved.get(0).getId();

        List<Faq> result = faqRepository.findByCategoryAndIsActiveTrueWithCursor("PAYMENT", cursorId, PageRequest.of(0, 10));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(f -> f.getId() > cursorId);
    }

    private Faq buildFaq(String category, boolean active) {
        Faq faq = Faq.builder()
                .question("테스트 질문입니다.")
                .answer("테스트 답변입니다.")
                .category(category)
                .build();
        if (!active) {
            faq.deactivate();
        }
        return faq;
    }
}
