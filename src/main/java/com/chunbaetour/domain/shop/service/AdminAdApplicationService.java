package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.shop.dto.response.AdminAdApplicationResponse;
import com.chunbaetour.domain.shop.entity.AdApplication;
import com.chunbaetour.domain.shop.repository.AdApplicationRepository;
import com.chunbaetour.domain.shop.type.AdApplicationStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 광고 신청 처리 서비스.
 * 신청 목록 조회, 승인, 거절 담당.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAdApplicationService {

    private final AdApplicationRepository adApplicationRepository;

    /**
     * 광고 승인.
     * AdApplication SELECT FOR UPDATE 후 상태 전이.
     */
    @Transactional
    public void approve(Long adId) {
        AdApplication application = adApplicationRepository.findByIdWithLock(adId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AD_APPLICATION_NOT_FOUND));

        // 상태 전이 가드 — AdApplication.approve() 내부에서도 검증
        application.approve();
    }

    /**
     * 광고 거절.
     * AdApplication SELECT FOR UPDATE 후 상태 전이.
     */
    @Transactional
    public void reject(Long adId, String reason) {
        AdApplication application = adApplicationRepository.findByIdWithLock(adId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AD_APPLICATION_NOT_FOUND));

        // 상태 전이 가드 — AdApplication.reject() 내부에서도 검증
        application.reject(reason);
    }

    /**
     * 관리자 광고 신청 목록 조회 — cursor keyset 페이징 (id DESC).
     * status=null이면 전체 조회, 지정 시 해당 상태만 필터링.
     */
    public CursorPageResponse<AdminAdApplicationResponse> getApplications(
            String cursor, int size, AdApplicationStatus status) {
        if (size < 1) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
        }
        Long cursorId = CursorUtils.decodeSafe(cursor);

        PageRequest pageable = PageRequest.of(0, size + 1);
        List<AdApplication> applications = findApplications(cursorId, status, pageable);

        boolean hasNext = applications.size() > size;
        List<AdApplication> page = hasNext ? applications.subList(0, size) : applications;

        List<AdminAdApplicationResponse> content = page.stream()
                .map(AdminAdApplicationResponse::from)
                .toList();

        String nextCursor = hasNext ? CursorUtils.encode(page.get(page.size() - 1).getId()) : null;
        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }

    private List<AdApplication> findApplications(Long cursorId, AdApplicationStatus status, PageRequest pageable) {
        if (status == null) {
            return cursorId == null
                    ? adApplicationRepository.findAllByOrderByIdDesc(pageable)
                    : adApplicationRepository.findByIdLessThanOrderByIdDesc(cursorId, pageable);
        }
        return cursorId == null
                ? adApplicationRepository.findByStatusOrderByIdDesc(status, pageable)
                : adApplicationRepository.findByStatusAndIdLessThanOrderByIdDesc(status, cursorId, pageable);
    }
}
