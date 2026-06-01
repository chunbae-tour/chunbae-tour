package com.chunbaetour.domain.admin.certification.service;

import com.chunbaetour.domain.admin.certification.dto.response.ShopCertificationDetailResponse;
import com.chunbaetour.domain.admin.certification.dto.response.ShopCertificationListResponse;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopCertification;
import com.chunbaetour.domain.shop.repository.ShopCertificationRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopCertificationStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 상인 인증 관리 서비스 (KAN-204, Admin Epic KAN-177 S05).
 *
 * <p>조회는 클래스 기본 {@code @Transactional(readOnly = true)}, 승인/거절/취소만 쓰기 {@code @Transactional}로
 * override. 승인/거절/취소 endpoint의 audit 기록은 컨트롤러의
 * {@link com.chunbaetour.domain.admin.audit.LogAdminAction} AOP가 담당하며 본 서비스는 도메인 상태 전이만 수행한다.
 *
 * <p>핵심 cascade
 * <ul>
 *   <li><b>승인</b> — {@code cert.approve(adminUserId, now)} + {@code shop.markCertified()}를 동일 트랜잭션에서
 *       수행해 인증 마크가 자동 부여된다.</li>
 *   <li><b>취소</b> — 대상 가게가 인증되지 않았으면 409({@link ErrorCode#SHOP_NOT_CERTIFIED}),
 *       인증된 가게면 {@code shop.unmarkCertified()}.</li>
 * </ul>
 *
 * <p>{@code processedAt}은 {@code Clock} 빈으로 산출해 도메인 메서드에 주입한다
 * ({@code AdminUserService.suspend}의 {@code LocalDateTime.now(clock)} 패턴 일관) — 테스트 시각 고정 가능.
 *
 * <p>{@link #getPendingCertificationsCount()}는 S10 대시보드(KAN-204 후속) 의존 — 본 슬라이스는 메서드 노출까지만.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminShopCertificationService {

    private final ShopCertificationRepository certificationRepository;
    private final ShopRepository shopRepository;
    private final Clock clock;

    /**
     * 인증 신청 목록 검색 — status 필터 + cursor 페이징.
     *
     * <p>size+1 sentinel로 다음 페이지 존재를 추가 쿼리 없이 판단({@code AdminUserService.getUsers} 패턴 재사용).
     */
    public CursorPageResponse<ShopCertificationListResponse> getCertifications(
            ShopCertificationStatus status, String cursor, int size) {
        Long cursorId = CursorUtils.decodeSafe(cursor);

        PageRequest pageable = PageRequest.of(0, size + 1);
        List<ShopCertification> certs = certificationRepository.searchForAdmin(status, cursorId, pageable);

        boolean hasNext = certs.size() > size;
        List<ShopCertification> content = hasNext ? certs.subList(0, size) : certs;
        String nextCursor = hasNext
                ? CursorUtils.encode(content.get(content.size() - 1).getId())
                : null;

        List<ShopCertificationListResponse> responses = content.stream()
                .map(ShopCertificationListResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    /** 인증 신청 단건 상세. */
    public ShopCertificationDetailResponse getCertification(Long applicationId) {
        return ShopCertificationDetailResponse.from(findCertification(applicationId));
    }

    /**
     * 인증 승인 — {@code cert.approve()}(PENDING 가드) + {@code shop.markCertified()} 동일 트랜잭션 cascade.
     * PENDING 외 신청 재승인 시 409, 미존재 신청 404, 미존재 가게 404.
     */
    @Transactional
    public ShopCertificationDetailResponse approve(Long applicationId, Long adminUserId) {
        ShopCertification cert = findCertification(applicationId);
        Shop shop = findShop(cert.getShopId());
        cert.approve(adminUserId, LocalDateTime.now(clock));
        shop.markCertified();
        return ShopCertificationDetailResponse.from(cert);
    }

    /**
     * 인증 거절 — {@code cert.reject()}(PENDING 가드) + rejectReason 기록. 가게 인증 마크는 부여하지 않는다.
     */
    @Transactional
    public ShopCertificationDetailResponse reject(Long applicationId, Long adminUserId, String reason) {
        ShopCertification cert = findCertification(applicationId);
        cert.reject(adminUserId, reason, LocalDateTime.now(clock));
        return ShopCertificationDetailResponse.from(cert);
    }

    /**
     * 인증 취소 — 이미 인증된 가게의 인증 마크 회수({@code shop.unmarkCertified()}). 인증되지 않은 가게면 409
     * ({@link ErrorCode#SHOP_NOT_CERTIFIED}). 취소 사유는 admin_action_log에 기록된다(컨트롤러 wiring 후속 보강).
     */
    @Transactional
    public void cancelCertification(Long shopId, String reason) {
        Shop shop = findShop(shopId);
        if (!shop.isCertified()) {
            throw new BusinessException(ErrorCode.SHOP_NOT_CERTIFIED);
        }
        shop.unmarkCertified();
    }

    // ── S10 대시보드(KAN-204 후속) 의존 카운트 ──────────────────────────────

    /** PENDING 인증 신청 수 — S10 대시보드 호출용. 본 슬라이스는 메서드 노출까지만 (wiring 후속). */
    public long getPendingCertificationsCount() {
        return certificationRepository.countByStatus(ShopCertificationStatus.PENDING);
    }

    private ShopCertification findCertification(Long applicationId) {
        return certificationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_CERTIFICATION_NOT_FOUND));
    }

    private Shop findShop(Long shopId) {
        return shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
    }
}
