package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.shop.dto.request.ShopNoticeCreateRequest;
import com.chunbaetour.domain.shop.dto.response.ShopNoticeResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopNotice;
import com.chunbaetour.domain.shop.repository.ShopNoticeRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가게 공지 서비스 (KAN-213).
 * 공지 등록/목록 조회/삭제 담당. ACTIVE 가게만 등록·삭제 허용.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopNoticeService {

    private final ShopRepository shopRepository;
    private final ShopNoticeRepository shopNoticeRepository;

    /**
     * 가게 공지 등록. ACTIVE 가게만 허용.
     * 소유권 검증(shopId + userId) 후 공지 저장.
     */
    @Transactional
    public ShopNoticeResponse createNotice(Long userId, Long shopId, ShopNoticeCreateRequest request) {
        // shopId + userId 조합으로 본인 ACTIVE 가게 조회
        Shop shop = getActiveShop(userId, shopId);

        // 공지 생성 및 저장
        ShopNotice notice = ShopNotice.create(shop.getId(), request.title(), request.content());

        return ShopNoticeResponse.from(shopNoticeRepository.save(notice));
    }

    /**
     * 가게 공지 목록 조회 (커서 페이징, 최신순).
     * cursor 없으면 첫 페이지. 소유권 검증 포함.
     */
    public CursorPageResponse<ShopNoticeResponse> getNotices(Long userId, Long shopId, String cursor, int size) {
        // size 방어 — 컨트롤러 @Min(1) 뚫리거나 서비스 직접 호출 시 대비
        if (size <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

        // shopId + userId 조합으로 본인 가게 조회 (상태 무관)
        shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // cursor 디코딩 (null = 첫 페이지)
        Long cursorId = CursorUtils.decodeSafe(cursor);

        // size+1 조회로 다음 페이지 존재 여부 판별
        List<ShopNotice> notices;
        PageRequest pageable = PageRequest.of(0, size + 1);

        if (cursorId == null) {
            // 첫 페이지 — cursor 없이 최신순
            notices = shopNoticeRepository.findByShopIdOrderByIdDesc(shopId, pageable);
        } else {
            // cursorId 미만 항목 조회
            notices = shopNoticeRepository.findByShopIdAndIdLessThanOrderByIdDesc(shopId, cursorId, pageable);
        }

        // hasNext 판별 후 size만큼 자르기
        boolean hasNext = notices.size() > size;
        List<ShopNotice> content = hasNext ? notices.subList(0, size) : notices;

        // 다음 cursor = 마지막 항목 id 인코딩
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        return new CursorPageResponse<>(
                content.stream().map(ShopNoticeResponse::from).toList(),
                nextCursor,
                hasNext,
                content.size()
        );
    }

    /**
     * 가게 공지 삭제. ACTIVE 가게만 허용.
     * noticeId + shopId 조합으로 소유권 검증 후 hard delete.
     */
    @Transactional
    public void deleteNotice(Long userId, Long shopId, Long noticeId) {
        // shopId + userId 조합으로 본인 ACTIVE 가게 조회
        Shop shop = getActiveShop(userId, shopId);

        // noticeId + shopId 조합 조회 — 타 가게 공지는 SHOP_NOTICE_NOT_FOUND로 처리
        ShopNotice notice = shopNoticeRepository.findByIdAndShopId(noticeId, shop.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOTICE_NOT_FOUND));

        shopNoticeRepository.delete(notice);
    }

    /** shopId + userId 조합으로 ACTIVE 가게 조회. 없거나 소유자 불일치면 SHOP_001, 비활성이면 SHOP_005. */
    private Shop getActiveShop(Long userId, Long shopId) {
        Shop shop = shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }

        return shop;
    }
}
