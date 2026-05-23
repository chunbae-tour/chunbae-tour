package com.chunbaetour.domain.merchant.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.merchant.dto.response.MerchantApplicationDetailResponse;
import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.merchant.repository.MerchantApplicationRepository;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 상인 신청 처리 서비스 (STORY-09).
 * 승인: account/shop 선제 검증 → application.approve() → account.promoteToMerchant() → Shop 생성 (단일 트랜잭션).
 * 거절: application.reject(rejectReason).
 * 락 획득 순서: MerchantApplication → Account (데드락 방지).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMerchantApplicationService {

    private static final Pattern CURSOR_PATTERN = Pattern.compile("\\{\"id\":(\\d{1,19})\\}");

    private final MerchantApplicationRepository applicationRepository;
    private final AccountRepository accountRepository;
    private final ShopRepository shopRepository;

    /**
     * PENDING 상인 신청 목록 cursor 페이징 조회.
     * cursor 형식: {"id":N} → Base64URL 인코딩 (padding 없음).
     */
    public CursorPageResponse<MerchantApplicationDetailResponse> getApplications(String cursor, int size) {
        // 페이지 크기 유효성 검증 — 1 이상 100 이하만 허용
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
        }

        // size+1개 조회 — 다음 페이지 존재 여부를 추가 쿼리 없이 판단하기 위한 sentinel 조회
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<MerchantApplication> applications = (cursor == null)
                // cursor 없으면 첫 페이지: id 내림차순으로 최신 PENDING 신청부터 조회
                ? applicationRepository.findByStatusOrderByIdDesc(MerchantApplicationStatus.PENDING, pageable)
                // cursor 있으면 다음 페이지: cursor id보다 작은 항목만 조회 (keyset pagination)
                : applicationRepository.findByStatusAndIdLessThanOrderByIdDesc(
                        MerchantApplicationStatus.PENDING, decodeCursor(cursor), pageable);

        // size+1번째 항목이 존재하면 다음 페이지 있음 — 실제 응답에는 size개만 포함
        boolean hasNext = applications.size() > size;
        List<MerchantApplication> content = hasNext ? applications.subList(0, size) : applications;
        // 다음 페이지 커서: 현재 페이지 마지막 항목(가장 작은 id)의 id를 인코딩
        String nextCursor = hasNext ? encodeCursor(content.get(content.size() - 1).getId()) : null;

        List<MerchantApplicationDetailResponse> responses = content.stream()
                .map(MerchantApplicationDetailResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
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

        // 이미 가게가 있는 계정이면 중복 생성 차단 — uk_shops_user_id DB 제약의 코드 레벨 선제 방어
        if (shopRepository.existsByUserId(account.getId())) {
            throw new BusinessException(ErrorCode.SHOP_ALREADY_EXISTS);
        }

        // 선행 검증 통과 후 상태 전이 — entity 오염 없이 예외 발생 가능한 검증을 모두 앞에서 처리
        application.approve();                                      // 신청 상태 PENDING → APPROVED
        account.promoteToMerchant();                                // 계정 역할 USER → MERCHANT
        shopRepository.save(Shop.fromApplication(application));     // 가게 엔티티 신규 생성

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
     * id를 Base64URL 인코딩된 커서 문자열로 변환.
     * 형식: {"id":N} → Base64URL (padding 없음) — URL 파라미터로 안전하게 전달 가능.
     */
    private String encodeCursor(Long id) {
        String json = "{\"id\":" + id + "}";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64URL 커서 문자열을 id(Long)로 복원.
     * 디코딩 실패 또는 형식 불일치({"id":N} 패턴 미준수) 시 INVALID_CURSOR 예외.
     * \d{1,19}로 Long 최대 자릿수 제한 — parseLong 오버플로우 방지.
     */
    private Long decodeCursor(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String json = new String(decoded, StandardCharsets.UTF_8);
            Matcher matcher = CURSOR_PATTERN.matcher(json);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("invalid cursor format");
            }
            return Long.parseLong(matcher.group(1));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
