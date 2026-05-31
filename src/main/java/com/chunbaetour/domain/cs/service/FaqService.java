package com.chunbaetour.domain.cs.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.cs.dto.request.FaqCreateRequest;
import com.chunbaetour.domain.cs.dto.request.FaqUpdateRequest;
import com.chunbaetour.domain.cs.dto.response.FaqResponse;
import com.chunbaetour.domain.cs.entity.Faq;
import com.chunbaetour.domain.cs.repository.FaqRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

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
