package com.chunbaetour.domain.cs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.cs.dto.response.FaqResponse;
import com.chunbaetour.domain.cs.entity.Faq;
import com.chunbaetour.domain.cs.repository.FaqRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @InjectMocks private FaqService faqService;
    @Mock private FaqRepository faqRepository;

    // ===== getActiveFaqs (USER 커서 페이징) =====

    // category null → findByIsActiveTrueWithCursor 호출
    @Test
    void getActiveFaqs_whenCategoryNull_callsFindByIsActiveTrue() {
        given(faqRepository.findByIsActiveTrueWithCursor(any(), any(PageRequest.class)))
                .willReturn(List.of(buildFaq(1L)));

        faqService.getActiveFaqs(null, 20, null);

        verify(faqRepository).findByIsActiveTrueWithCursor(any(), any(PageRequest.class));
        verifyNoMoreInteractions(faqRepository);
    }

    // category blank → findByIsActiveTrueWithCursor 호출
    @Test
    void getActiveFaqs_whenCategoryBlank_callsFindByIsActiveTrue() {
        given(faqRepository.findByIsActiveTrueWithCursor(any(), any(PageRequest.class)))
                .willReturn(List.of());

        faqService.getActiveFaqs(null, 20, "  ");

        verify(faqRepository).findByIsActiveTrueWithCursor(any(), any(PageRequest.class));
    }

    // category 있음 → findByCategoryAndIsActiveTrueWithCursor 호출
    @Test
    void getActiveFaqs_whenCategoryPresent_callsFindByCategory() {
        given(faqRepository.findByCategoryAndIsActiveTrueWithCursor(any(), any(), any(PageRequest.class)))
                .willReturn(List.of(buildFaq(1L)));

        faqService.getActiveFaqs(null, 20, "PAYMENT");

        verify(faqRepository).findByCategoryAndIsActiveTrueWithCursor(any(), any(), any(PageRequest.class));
        verifyNoMoreInteractions(faqRepository);
    }

    // size보다 많은 결과 → hasNext=true, nextCursor 존재
    @Test
    void getActiveFaqs_hasNextTrue_whenResultExceedsSize() {
        int size = 2;
        given(faqRepository.findByIsActiveTrueWithCursor(any(), any(PageRequest.class)))
                .willReturn(List.of(buildFaq(1L), buildFaq(2L), buildFaq(3L)));

        CursorPageResponse<FaqResponse> result = faqService.getActiveFaqs(null, size, null);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
        assertThat(result.content()).hasSize(size);
    }

    // size 이하 결과 → hasNext=false, nextCursor null
    @Test
    void getActiveFaqs_hasNextFalse_whenResultWithinSize() {
        given(faqRepository.findByIsActiveTrueWithCursor(any(), any(PageRequest.class)))
                .willReturn(List.of(buildFaq(1L)));

        CursorPageResponse<FaqResponse> result = faqService.getActiveFaqs(null, 20, null);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    private Faq buildFaq(Long id) {
        Faq faq = Faq.builder()
                .question("테스트 질문")
                .answer("테스트 답변")
                .category("PAYMENT")
                .build();
        // 리플렉션으로 id 주입 — @GeneratedValue는 DB 없이 채울 수 없어 테스트 전용
        try {
            var field = Faq.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(faq, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return faq;
    }
}
