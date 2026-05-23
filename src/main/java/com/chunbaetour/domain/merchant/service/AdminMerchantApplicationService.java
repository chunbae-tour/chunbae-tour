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
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
        }

        PageRequest pageable = PageRequest.of(0, size + 1);
        List<MerchantApplication> applications = (cursor == null)
                ? applicationRepository.findByStatusOrderByIdDesc(MerchantApplicationStatus.PENDING, pageable)
                : applicationRepository.findByStatusAndIdLessThanOrderByIdDesc(
                        MerchantApplicationStatus.PENDING, decodeCursor(cursor), pageable);

        boolean hasNext = applications.size() > size;
        List<MerchantApplication> content = hasNext ? applications.subList(0, size) : applications;
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
        MerchantApplication application = applicationRepository.findByIdWithLock(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCHANT_APPLICATION_NOT_FOUND));

        Account account = accountRepository.findByIdWithLock(application.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (shopRepository.existsByUserId(account.getId())) {
            throw new BusinessException(ErrorCode.SHOP_ALREADY_EXISTS);
        }

        application.approve();
        account.promoteToMerchant();
        shopRepository.save(Shop.fromApplication(application));

        return MerchantApplicationDetailResponse.from(application);
    }

    /**
     * 상인 신청 거절.
     */
    @Transactional
    public MerchantApplicationDetailResponse reject(Long applicationId, String rejectReason) {
        MerchantApplication application = applicationRepository.findByIdWithLock(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCHANT_APPLICATION_NOT_FOUND));

        application.reject(rejectReason);

        return MerchantApplicationDetailResponse.from(application);
    }

    private String encodeCursor(Long id) {
        String json = "{\"id\":" + id + "}";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

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
