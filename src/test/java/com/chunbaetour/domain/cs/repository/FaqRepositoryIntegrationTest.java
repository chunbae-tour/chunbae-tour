package com.chunbaetour.domain.cs.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.cs.entity.Faq;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FaqRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FaqRepository faqRepository;

    @AfterEach
    void tearDown() {
        faqRepository.deleteAll();
    }

    // ===== findByCategoryOrderByIdAsc =====

    // 지정 카테고리의 FAQ만 반환 — 다른 카테고리 제외, 활성/비활성 포함
    @Test
    void findByCategoryOrderByIdAsc_returnsOnlyMatchingCategory() {
        faqRepository.saveAll(List.of(
                buildFaq("PAYMENT", true),
                buildFaq("PAYMENT", false),
                buildFaq("ACCOUNT", true)
        ));

        List<Faq> result = faqRepository.findByCategoryOrderByIdAsc("PAYMENT");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(f -> f.getCategory().equals("PAYMENT"));
    }

    // id ASC 정렬 확인
    @Test
    void findByCategoryOrderByIdAsc_returnsIdAscOrder() {
        faqRepository.saveAll(List.of(
                buildFaq("PAYMENT", true),
                buildFaq("PAYMENT", true),
                buildFaq("PAYMENT", true)
        ));

        List<Faq> result = faqRepository.findByCategoryOrderByIdAsc("PAYMENT");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isLessThan(result.get(1).getId());
        assertThat(result.get(1).getId()).isLessThan(result.get(2).getId());
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

    // 다른 카테고리 FAQ는 제외됨
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

    // 비활성 FAQ는 전체 조회에서도 제외됨
    @Test
    void findByIsActiveTrueOrderByIdAsc_excludesInactiveFaqs() {
        faqRepository.saveAll(List.of(
                buildFaq("PAYMENT", true),
                buildFaq("PAYMENT", false),
                buildFaq("ACCOUNT", true)
        ));

        List<Faq> result = faqRepository.findByIsActiveTrueOrderByIdAsc();

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(Faq::isActive);
    }

    // 카테고리 무관 전체 활성 FAQ 반환 + id ASC 정렬
    @Test
    void findByIsActiveTrueOrderByIdAsc_returnsAllCategoriesInIdAscOrder() {
        faqRepository.saveAll(List.of(
                buildFaq("PAYMENT", true),
                buildFaq("ACCOUNT", true),
                buildFaq("SHIPPING", true)
        ));

        List<Faq> result = faqRepository.findByIsActiveTrueOrderByIdAsc();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isLessThan(result.get(1).getId());
        assertThat(result.get(1).getId()).isLessThan(result.get(2).getId());
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
