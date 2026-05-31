package com.chunbaetour.domain.merchant.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.merchant.dto.response.MerchantApplicationDetailResponse;
import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.merchant.repository.MerchantApplicationRepository;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 상인 신청 처리 서비스 (STORY-09).
 * 승인: account/shop 선제 검증 → application.approve() → account.promoteToMerchant() → Shop 생성 (단일 트랜잭션).
 * 거절: application.reject(rejectReason).
 *
 * [락 순서 규칙] MerchantApplication → Account → Shop → Wallet (이 순서 고정, 역방향 금지).
 * Account에 SELECT FOR UPDATE를 추가할 경우 반드시 MerchantApplication 락 이후에 획득해야 데드락 방지.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMerchantApplicationService {

    private final MerchantApplicationRepository applicationRepository;
    private final AccountRepository accountRepository;
    private final ShopRepository shopRepository;

    /**
     * 상인 신청 목록 cursor 페이징 조회 (status 필터).
     * cursor 형식: id → Base64URL 인코딩 (CursorUtils 공통 유틸 사용).
     * size 유효성(@Min(1)/@Max(100))은 컨트롤러에서 처리.
     */
    public CursorPageResponse<MerchantApplicationDetailResponse> getApplications(String cursor, int size, MerchantApplicationStatus status) {
        // size+1개 조회 — 다음 페이지 존재 여부를 추가 쿼리 없이 판단하기 위한 sentinel 조회
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<MerchantApplication> applications = (cursor == null)
                // cursor 없으면 첫 페이지: id 내림차순으로 최신 신청부터 조회
                ? applicationRepository.findByStatusOrderByIdDesc(status, pageable)
                // cursor 있으면 다음 페이지: cursor id보다 작은 항목만 조회 (keyset pagination)
                : applicationRepository.findByStatusAndIdLessThanOrderByIdDesc(
                        status, parseCursor(cursor), pageable);

        // size+1번째 항목이 존재하면 다음 페이지 있음 — 실제 응답에는 size개만 포함
        boolean hasNext = applications.size() > size;
        List<MerchantApplication> content = hasNext ? applications.subList(0, size) : applications;
        // 다음 페이지 커서: 현재 페이지 마지막 항목(가장 작은 id)의 id를 인코딩
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        List<MerchantApplicationDetailResponse> responses = content.stream()
                .map(MerchantApplicationDetailResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    /**
     * 상인 신청 단건 상세 조회 (KAN-182).
     *
     * <p>운영자가 승인/거절 결정 전 신청 상세 정보 확인용. 상태 전이가 없는 순수 조회라
     * {@code @Transactional(readOnly = true)} (클래스 default 그대로) + 비관적 락 없이 단순 {@code findById}.
     * 같은 신청을 다른 관리자가 동시 처리해도 본 GET 응답이 stale일 수는 있으나 그 자체는 사고가 아님 —
     * 실제 상태 전이는 approve/reject가 PESSIMISTIC_WRITE로 직렬화하므로 정합성 손상 없음.
     *
     * @param applicationId 조회 대상 신청 ID
     * @return 신청 상세 (목록/승인/거절과 동일 DTO 재사용 — 본 슬라이스는 필드 확장 없음)
     * @throws BusinessException {@code MERCHANT_APPLICATION_NOT_FOUND} — 존재하지 않는 ID
     */
    public MerchantApplicationDetailResponse getApplication(Long applicationId) {
        MerchantApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCHANT_APPLICATION_NOT_FOUND));
        return MerchantApplicationDetailResponse.from(application);
    }

    /**
     * 상인 신청 승인.
     * 두 관리자가 동일 신청을 동시 승인할 경우, 두 트랜잭션 모두 PENDING을 읽고
     * 상태 가드를 통과해 Shop이 중복 생성될 수 있다. findByIdWithLock(SELECT FOR UPDATE)으로
     * 첫 번째 트랜잭션이 커밋될 때까지 두 번째 요청을 블로킹해 이를 방지한다.
     * 단일 트랜잭션: application → account → shop 순으로 락 획득 후 처리.
     * 이미 가게가 있으면 SHOP_ALREADY_EXISTS.
     */
    @Transactional
    public MerchantApplicationDetailResponse approve(Long applicationId) {
        // 비관적 락(SELECT FOR UPDATE) — 동일 신청에 대한 동시 승인 요청을 직렬화
        MerchantApplication application = applicationRepository.findByIdWithLock(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCHANT_APPLICATION_NOT_FOUND));

        // 신청자 계정 락 획득 — 락 순서: MerchantApplication → Account (데드락 방지)
        Account account = accountRepository.findByIdWithLock(application.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 선행 검증 통과 후 상태 전이 — entity 오염 없이 예외 발생 가능한 검증을 모두 앞에서 처리
        application.approve();                // 신청 상태 PENDING → APPROVED
        account.promoteToMerchant();          // USER → MERCHANT 승격 (MERCHANT면 멱등 skip, ADMIN이면 예외)
        try {
            shopRepository.save(Shop.fromApplication(application)); // 가게 엔티티 신규 생성
        } catch (DataIntegrityViolationException e) {
            // uk_shops_application_id 제약 위반 — 동일 신청서로 가게 2개 생성 방지 (동시 승인 race condition)
            String msg = e.getRootCause() != null ? e.getRootCause().getMessage() : e.getMessage();
            if (msg != null && msg.contains("uk_shops_application_id")) {
                throw new BusinessException(ErrorCode.SHOP_ALREADY_EXISTS);
            }
            log.error("Shop 저장 중 예상치 못한 DB 제약 위반 발생. applicationId={}", application.getId(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return MerchantApplicationDetailResponse.from(application);
    }

    /**
     * 상인 신청 거절.
     * 비관적 락으로 조회 후 PENDING 상태 가드를 통과해야만 REJECTED 전이.
     * 이미 승인/거절된 신청이면 entity.reject()에서 MERCHANT_APPLICATION_STATUS_INVALID 발생.
     */
    @Transactional
    public MerchantApplicationDetailResponse reject(Long applicationId, String rejectReason) {
        // 비관적 락(SELECT FOR UPDATE) — approve 요청과 동일 행 잠금으로 approve↔reject 경합 직렬화
        MerchantApplication application = applicationRepository.findByIdWithLock(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCHANT_APPLICATION_NOT_FOUND));

        // 신청 상태 PENDING → REJECTED, 거절 사유 저장, activeFlag null 처리
        application.reject(rejectReason);

        return MerchantApplicationDetailResponse.from(application);
    }

    /**
     * Base64URL 커서 문자열을 id(Long)로 복원.
     * CursorUtils.decode 실패 또는 id < 1이면 INVALID_CURSOR 예외.
     */
    private long parseCursor(String cursor) {
        try {
            long id = CursorUtils.decode(cursor);
            if (id < 1) {
                throw new IllegalArgumentException("cursor id must be >= 1");
            }
            return id;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
