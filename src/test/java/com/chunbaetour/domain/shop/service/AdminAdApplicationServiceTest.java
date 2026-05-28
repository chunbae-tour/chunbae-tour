package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.isNull;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.shop.dto.response.AdminAdApplicationResponse;
import com.chunbaetour.domain.shop.entity.AdApplication;
import com.chunbaetour.domain.shop.repository.AdApplicationRepository;
import com.chunbaetour.domain.shop.type.AdApplicationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminAdApplicationServiceTest {

    @Mock private AdApplicationRepository adApplicationRepository;

    @InjectMocks
    private AdminAdApplicationService adminAdApplicationService;

    private static final Long AD_ID = 1L;
    private static final Long SHOP_ID = 10L;

    private AdApplication createPending(Long id) {
        AdApplication a = AdApplication.create(SHOP_ID, "BANNER",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 10_000L);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private AdApplication createApproved(Long id) {
        AdApplication a = createPending(id);
        a.approve();
        return a;
    }

    @Test
    @DisplayName("광고 승인 성공 — status APPROVED 전이")
    void approve_success() {
        AdApplication application = createPending(AD_ID);
        given(adApplicationRepository.findByIdWithLock(AD_ID)).willReturn(Optional.of(application));

        adminAdApplicationService.approve(AD_ID);

        assertThat(application.getStatus()).isEqualTo(AdApplicationStatus.APPROVED);
    }

    @Test
    @DisplayName("광고 승인 — 존재하지 않는 신청 AD_APPLICATION_NOT_FOUND")
    void approve_notFound() {
        given(adApplicationRepository.findByIdWithLock(AD_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminAdApplicationService.approve(AD_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AD_APPLICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("광고 승인 — PENDING 아닌 상태 AD_APPLICATION_INVALID_STATUS")
    void approve_invalidStatus() {
        AdApplication application = createApproved(AD_ID);
        given(adApplicationRepository.findByIdWithLock(AD_ID)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> adminAdApplicationService.approve(AD_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AD_APPLICATION_INVALID_STATUS);
    }

    @Test
    @DisplayName("광고 거절 성공 — status REJECTED 전이 및 사유 저장")
    void reject_success() {
        AdApplication application = createPending(AD_ID);
        given(adApplicationRepository.findByIdWithLock(AD_ID)).willReturn(Optional.of(application));

        adminAdApplicationService.reject(AD_ID, "광고 내용 부적절");

        assertThat(application.getStatus()).isEqualTo(AdApplicationStatus.REJECTED);
        assertThat(application.getRejectReason()).isEqualTo("광고 내용 부적절");
    }

    @Test
    @DisplayName("광고 거절 — 존재하지 않는 신청 AD_APPLICATION_NOT_FOUND")
    void reject_notFound() {
        given(adApplicationRepository.findByIdWithLock(AD_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminAdApplicationService.reject(AD_ID, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AD_APPLICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("광고 거절 — PENDING 아닌 상태 AD_APPLICATION_INVALID_STATUS")
    void reject_invalidStatus() {
        AdApplication application = createApproved(AD_ID);
        given(adApplicationRepository.findByIdWithLock(AD_ID)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> adminAdApplicationService.reject(AD_ID, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AD_APPLICATION_INVALID_STATUS);
    }

    @Test
    @DisplayName("광고 신청 목록 조회 — 첫 페이지 (hasNext=false)")
    void getApplications_firstPage() {
        AdApplication a1 = createPending(2L);
        AdApplication a2 = createPending(1L);

        given(adApplicationRepository.findAllWithCursor(isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of(a1, a2));

        CursorPageResponse<AdminAdApplicationResponse> result =
                adminAdApplicationService.getApplications(null, 20, null);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("광고 신청 목록 조회 — 다음 페이지 존재 (hasNext=true)")
    void getApplications_hasNext() {
        AdApplication a1 = createPending(3L);
        AdApplication a2 = createPending(2L);
        AdApplication a3 = createPending(1L);

        given(adApplicationRepository.findAllWithCursor(isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of(a1, a2, a3));

        CursorPageResponse<AdminAdApplicationResponse> result =
                adminAdApplicationService.getApplications(null, 2, null);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }
}
