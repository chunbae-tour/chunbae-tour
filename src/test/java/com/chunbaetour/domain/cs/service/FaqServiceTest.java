package com.chunbaetour.domain.cs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.cs.dto.request.FaqCreateRequest;
import com.chunbaetour.domain.cs.dto.request.FaqUpdateRequest;
import com.chunbaetour.domain.cs.dto.response.FaqResponse;
import com.chunbaetour.domain.cs.dto.response.FaqTranslationResponse;
import com.chunbaetour.domain.cs.entity.Faq;
import com.chunbaetour.domain.cs.repository.FaqRepository;
import com.chunbaetour.domain.translation.dto.response.TranslationResponse;
import com.chunbaetour.domain.translation.service.TranslationService;
import com.chunbaetour.domain.translation.type.LanguageCode;
import com.chunbaetour.domain.translation.type.TranslationSourceType;
import java.util.List;
import java.util.Optional;
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
    @Mock private TranslationService translationService;

    // ===== getAll (ADMIN cursor 페이징) =====

    // size보다 많은 결과 → hasNext=true, nextCursor 존재
    @Test
    void getAll_hasNextTrue_whenResultExceedsSize() {
        int size = 2;
        List<Faq> page = List.of(buildFaq(1L), buildFaq(2L), buildFaq(3L)); // size+1개
        given(faqRepository.findWithCursor(any(), any(PageRequest.class))).willReturn(page);

        CursorPageResponse<FaqResponse> result = faqService.getAll(null, size);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
        assertThat(result.content()).hasSize(size);
    }

    // size 이하 결과 → hasNext=false, nextCursor null
    @Test
    void getAll_hasNextFalse_whenResultWithinSize() {
        int size = 5;
        List<Faq> page = List.of(buildFaq(1L), buildFaq(2L)); // size 미만
        given(faqRepository.findWithCursor(any(), any(PageRequest.class))).willReturn(page);

        CursorPageResponse<FaqResponse> result = faqService.getAll(null, size);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.content()).hasSize(2);
    }

    // ===== getActiveFaqs (USER cursor 페이징) =====

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

    // ===== update =====

    // null 필드는 기존 값 유지 — 부분 갱신 검증
    @Test
    void update_preservesExistingValuesForNullFields() {
        Faq faq = buildFaq(1L);
        given(faqRepository.findById(1L)).willReturn(Optional.of(faq));

        FaqUpdateRequest request = new FaqUpdateRequest(null, null, null);
        FaqResponse result = faqService.update(1L, request);

        assertThat(result.question()).isEqualTo("테스트 질문");
        assertThat(result.answer()).isEqualTo("테스트 답변");
        assertThat(result.category()).isEqualTo("PAYMENT");
    }

    // 존재하지 않는 FAQ → FAQ_NOT_FOUND
    @Test
    void update_throwsFaqNotFound_whenFaqNotExists() {
        given(faqRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> faqService.update(999L, new FaqUpdateRequest("q", "a", "c")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FAQ_NOT_FOUND));
    }

    // ===== delete (soft) =====

    // soft delete — isActive=false, DB 레코드 유지
    @Test
    void delete_deactivatesFaq_notPhysicalDelete() {
        Faq faq = buildFaq(1L);
        given(faqRepository.findById(1L)).willReturn(Optional.of(faq));

        faqService.delete(1L);

        assertThat(faq.isActive()).isFalse();
    }

    // 존재하지 않는 FAQ → FAQ_NOT_FOUND
    @Test
    void delete_throwsFaqNotFound_whenFaqNotExists() {
        given(faqRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> faqService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FAQ_NOT_FOUND));
    }

    // ===== getFaqTranslation =====

    // 활성 FAQ → question/answer 각각 번역 후 반환
    @Test
    void getFaqTranslation_returnsTranslatedQuestionAndAnswer() {
        Faq faq = buildFaq(1L);
        given(faqRepository.findById(1L)).willReturn(Optional.of(faq));
        given(translationService.translate("테스트 질문", LanguageCode.EN, TranslationSourceType.FAQ))
                .willReturn(new TranslationResponse("Test question", LanguageCode.EN));
        given(translationService.translate("테스트 답변", LanguageCode.EN, TranslationSourceType.FAQ))
                .willReturn(new TranslationResponse("Test answer", LanguageCode.EN));

        FaqTranslationResponse result = faqService.getFaqTranslation(1L, LanguageCode.EN);

        assertThat(result.faqId()).isEqualTo(1L);
        assertThat(result.question()).isEqualTo("Test question");
        assertThat(result.answer()).isEqualTo("Test answer");
        assertThat(result.targetLanguage()).isEqualTo(LanguageCode.EN);
    }

    // 존재하지 않는 FAQ → FAQ_NOT_FOUND
    @Test
    void getFaqTranslation_throwsFaqNotFound_whenFaqNotExists() {
        given(faqRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> faqService.getFaqTranslation(999L, LanguageCode.EN))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FAQ_NOT_FOUND));
    }

    // 비활성 FAQ → FAQ_NOT_FOUND (목록 조회와 동일하게 USER 노출 제외)
    @Test
    void getFaqTranslation_throwsFaqNotFound_whenFaqInactive() {
        Faq faq = buildFaq(1L);
        faq.deactivate();
        given(faqRepository.findById(1L)).willReturn(Optional.of(faq));

        assertThatThrownBy(() -> faqService.getFaqTranslation(1L, LanguageCode.EN))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FAQ_NOT_FOUND));
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
