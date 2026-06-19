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
        // 서비스 직접 호출 대비 null 방어
        if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);

        // shopId + userId 조합으로 본인 ACTIVE 가게 조회
        Shop shop = getActiveShop(userId, shopId);

        // 공지 생성 및 저장
        ShopNotice notice = ShopNotice.create(shop.getId(), request.title(), request.content());

        return ShopNoticeResponse.from(shopNoticeRepository.save(notice));
    }

    /**
     * 가게 공지 목록 조회 (커서 페이징, 최신순).
     * 조회는 가게 상태 무관 허용 (SUSPENDED/CLOSED 상태에서도 상인이 공지 확인 가능해야 함).
     * 등록·삭제는 ACTIVE만 허용 — 정책 비대칭 의도적.
     * cursor 없으면 첫 페이지. 소유권 검증 포함.
     */
    public CursorPageResponse<ShopNoticeResponse> getNotices(Long userId, Long shopId, String cursor, int size) {
        // size 방어 — 컨트롤러 @Min(1)/@Max(100) 우회 및 서비스 직접 호출 대비
        if (size <= 0 || size > 100) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

        // shopId + userId 조합으로 본인 가게 조회 (상태 무관)
        shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        return queryNotices(shopId, cursor, size);
    }

    /**
     * 공개 공지 목록 조회 (KAN-323, 커서 페이징·최신순) — 비인증.
     * 소유자 검증 없이 shopId로 조회한다(일반/비로그인 사용자가 가게 공개 정보에서 공지 열람).
     * SUSPENDED 가게는 차단(SHOP_NOT_FOUND로 통일, 존재 여부 노출 방지), CLOSED는 허용 —
     * 폐업/임시 휴무 공지도 손님이 볼 수 있어야 함({@link ShopService#getShopInfo} 공개 정책과 동일).
     */
    public CursorPageResponse<ShopNoticeResponse> getPublicNotices(Long shopId, String cursor, int size) {
        // size 방어 — 컨트롤러 @Min(1)/@Max(100) 우회 및 서비스 직접 호출 대비
        if (size <= 0 || size > 100) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

        // 소유자 검증 없이 shopId로 가게 조회 — 없으면 SHOP_001
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // SUSPENDED는 공개 차단(존재 노출 방지). CLOSED는 허용 — getShopInfo와 동일 정책
        if (shop.getStatus() == ShopStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.SHOP_NOT_FOUND);
        }

        return queryNotices(shopId, cursor, size);
    }

    /**
     * 공지 커서 페이징 공통 조회 — 소유권/상태 가드는 호출측 책임. cursor 디코딩 + size+1 조회로 다음 페이지 판별,
     * 매핑·커서 인코딩은 공통 팩토리로 위임(KAN-295). 상인용/공개용이 동일 쿼리를 재사용한다.
     */
    private CursorPageResponse<ShopNoticeResponse> queryNotices(Long shopId, String cursor, int size) {
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

        return CursorPageResponse.of(notices, size, ShopNoticeResponse::from, ShopNotice::getId);
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
