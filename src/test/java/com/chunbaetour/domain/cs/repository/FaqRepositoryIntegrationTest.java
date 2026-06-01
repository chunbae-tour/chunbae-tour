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

    // ===== findByCategoryAndIsActiveTrueOrderByIdAsc =====

    // 비활성(isActive=false) FAQ는 제외됨
    @Test
    void findByCategoryAndIsActiveTrueOrderByIdAsc_excludesInactiveFaqs() {
        Faq active = faqRepository.save(buildFaq("PAYMENT", true));
        faqRepository.save(buildFaq("PAYMENT", false));

        List<Faq> result = faqRepository.findByCategoryAndIsActiveTrueOrderByIdAsc("PAYMENT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(active.getId());
    }

    // 다른 카테고리 제외
    @Test
    void findByCategoryAndIsActiveTrueOrderByIdAsc_excludesOtherCategories() {
        faqRepository.saveAll(List.of(
                buildFaq("PAYMENT", true),
                buildFaq("ACCOUNT", true)
        ));

        List<Faq> result = faqRepository.findByCategoryAndIsActiveTrueOrderByIdAsc("PAYMENT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("PAYMENT");
    }

    // ===== findByIsActiveTrueOrderByIdAsc =====

    // 비활성 제외, 카테고리 무관, id ASC 정렬
    @Test
    void findByIsActiveTrueOrderByIdAsc_excludesInactiveAndSortsAsc() {
        faqRepository.saveAll(List.of(
                buildFaq("PAYMENT", true),
                buildFaq("PAYMENT", false),
                buildFaq("ACCOUNT", true)
        ));

        List<Faq> result = faqRepository.findByIsActiveTrueOrderByIdAsc();

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(Faq::isActive);
        assertThat(result.get(0).getId()).isLessThan(result.get(1).getId());
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
