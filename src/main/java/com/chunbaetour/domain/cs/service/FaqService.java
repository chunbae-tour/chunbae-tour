package com.chunbaetour.domain.cs.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.cs.dto.request.FaqCreateRequest;
import com.chunbaetour.domain.cs.dto.request.FaqUpdateRequest;
import com.chunbaetour.domain.cs.dto.response.FaqResponse;
import com.chunbaetour.domain.cs.entity.Faq;
import com.chunbaetour.domain.cs.repository.FaqRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    // ADMIN: 전체 FAQ 목록 조회 (활성/비활성 포함, 카테고리 필터 선택)
    public List<FaqResponse> getAll(String category) {
        List<Faq> faqs = (category != null && !category.isBlank())
                ? faqRepository.findByCategoryOrderByIdAsc(category)
                : faqRepository.findAll();
        return faqs.stream().map(FaqResponse::from).toList();
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
