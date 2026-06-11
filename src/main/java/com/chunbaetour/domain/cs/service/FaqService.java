package com.chunbaetour.domain.cs.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.cs.dto.request.FaqCreateRequest;
import com.chunbaetour.domain.cs.dto.request.FaqUpdateRequest;
import com.chunbaetour.domain.cs.dto.response.FaqResponse;
import com.chunbaetour.domain.cs.dto.response.FaqTranslationResponse;
import com.chunbaetour.domain.cs.entity.Faq;
import com.chunbaetour.domain.cs.repository.FaqRepository;
import com.chunbaetour.domain.translation.service.TranslationService;
import com.chunbaetour.domain.translation.type.LanguageCode;
import com.chunbaetour.domain.translation.type.TranslationSourceType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;
    private final TranslationService translationService;

    // ADMIN: FAQ 목록 커서 페이징 (활성/비활성 포함)
    public CursorPageResponse<FaqResponse> getAll(String cursor, int size) {
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<Faq> page = faqRepository.findWithCursor(cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = page.size() > size;
        List<FaqResponse> content = page.stream()
                .limit(size)
                .map(FaqResponse::from)
                .toList();
        // content 기준으로 nextCursor 인코딩 — mapping/필터링 변경 시에도 안전
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).faqId()) : null;

        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }

    // USER: 활성 FAQ 커서 페이징 — isActive=true만 반환, category 필터 선택
    public CursorPageResponse<FaqResponse> getActiveFaqs(String cursor, int size, String category) {
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<Faq> page = (category != null && !category.isBlank())
                ? faqRepository.findByCategoryAndIsActiveTrueWithCursor(category, cursorId, PageRequest.of(0, size + 1))
                : faqRepository.findByIsActiveTrueWithCursor(cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = page.size() > size;
        List<FaqResponse> content = page.stream().limit(size).map(FaqResponse::from).toList();
        // content 기준으로 nextCursor 인코딩 — mapping/필터링 변경 시에도 안전
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).faqId()) : null;

        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }

    // USER: 활성 FAQ 단건 번역 — entity-ID 기반, question/answer를 targetLanguage로 번역 (FAQ 캐시 경유)
    // 트랜잭션 미사용 — Google API 호출(translate) 동안 DB 커넥션을 점유하지 않기 위해 NOT_SUPPORTED.
    // findById는 Spring Data JPA가 자체 짧은 트랜잭션으로 처리, 캐시 저장도 TranslationCacheService에서 REQUIRES_NEW로 별도 처리됨
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FaqTranslationResponse getFaqTranslation(Long faqId, LanguageCode targetLanguage) {
        Faq faq = faqRepository.findById(faqId)
                .filter(Faq::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAQ_NOT_FOUND));

        String translatedQuestion = translationService
                .translate(faq.getQuestion(), targetLanguage, TranslationSourceType.FAQ)
                .translatedContent();
        String translatedAnswer = translationService
                .translate(faq.getAnswer(), targetLanguage, TranslationSourceType.FAQ)
                .translatedContent();

        return new FaqTranslationResponse(faq.getId(), translatedQuestion, translatedAnswer, targetLanguage);
    }

    // ADMIN: FAQ 등록
    @Transactional
    public FaqResponse create(FaqCreateRequest request) {
        Faq faq = Faq.builder()
                .question(request.question())
                .answer(request.answer())
                .category(request.category())
                .build();
        return FaqResponse.from(faqRepository.save(faq));
    }

    // ADMIN: FAQ 수정 — null/blank 필드는 기존 값 유지
    @Transactional
    public FaqResponse update(Long faqId, FaqUpdateRequest request) {
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAQ_NOT_FOUND));
        faq.update(request.question(), request.answer(), request.category());
        return FaqResponse.from(faq);
    }

    // ADMIN: FAQ 삭제 — soft delete (isActive=false), DB 레코드 유지
    @Transactional
    public void delete(Long faqId) {
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAQ_NOT_FOUND));
        faq.deactivate();
    }
}
