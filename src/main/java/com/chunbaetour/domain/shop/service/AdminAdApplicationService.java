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
     * 관리자 광고 신청 단건 상세 조회 (KAN-269).
     * 승인·거절 판단 전 신청 1건의 상세 확인용. 없는 adId면 AD_APPLICATION_NOT_FOUND.
     * 목록 조회와 동일한 응답 형식(AdminAdApplicationResponse) 유지.
     */
    public AdminAdApplicationResponse getApplication(Long adId) {
        AdApplication application = adApplicationRepository.findById(adId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AD_APPLICATION_NOT_FOUND));
        return AdminAdApplicationResponse.from(application);
    }

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

        // 다음 페이지 판별·매핑·커서 인코딩을 공통 팩토리로 위임 (KAN-295)
        return CursorPageResponse.of(applications, size, AdminAdApplicationResponse::from, AdApplication::getId);
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
