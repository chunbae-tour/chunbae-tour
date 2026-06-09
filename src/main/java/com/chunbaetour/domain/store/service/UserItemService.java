package com.chunbaetour.domain.store.service;

import com.chunbaetour.domain.auth.jwt.ItemQrClaims;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.chunbaetour.domain.store.dto.response.UserItemQrResponse;
import com.chunbaetour.domain.store.dto.response.UserItemResponse;
import com.chunbaetour.domain.store.dto.response.UserItemUseResponse;
import com.chunbaetour.domain.store.entity.UserItem;
import com.chunbaetour.domain.store.repository.UserItemRepository;
import com.chunbaetour.domain.store.type.UserItemStatus;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 보유 아이템 서비스.
 * 담당 기능: cursor 페이징 기반 보유 아이템 조회, 사용자 아이템 QR 발급, 아이템 사용 처리.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserItemService {

    private final UserItemRepository userItemRepository;
    private final ShopRepository shopRepository;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    /** 내 보유 아이템 조회 — cursor keyset 페이징 (id DESC) */
    public CursorPageResponse<UserItemResponse> getMyItems(Long userId, String cursor, int size) {
        if (size <= 0) {
            return new CursorPageResponse<>(List.of(), null, false, 0);
        }
        // cursor 디코딩 — null이면 첫 페이지
        Long cursorId = CursorUtils.decodeSafe(cursor);

        // size+1 조회로 다음 페이지 존재 여부 판별
        List<UserItem> items = userItemRepository.findItemsByUserIdWithCursor(
                userId, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = items.size() > size;
        List<UserItem> page = hasNext ? items.subList(0, size) : items;

        List<UserItemResponse> content = page.stream()
                .map(UserItemResponse::from)
                .toList();

        String nextCursor = hasNext ? CursorUtils.encode(page.get(page.size() - 1).getId()) : null;
        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }

    public UserItemQrResponse issueQr(Long userId, Long itemId) {
        UserItem item = userItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        validateOwner(item, userId);
        validateAvailable(item);

        String token = tokenIssuer.issueItemQr(userId, itemId);
        ItemQrClaims claims = tokenIssuer.verifyItemQr(token);
        return new UserItemQrResponse(token, claims.expiresAt());
    }

    @Transactional
    public UserItemUseResponse useByQr(Long verifierUserId, Long shopId, String token) {
        Shop shop = shopRepository.findByIdAndUserId(shopId, verifierUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }

        ItemQrClaims claims = verifyItemQr(token);
        UserItem item = userItemRepository.findByIdWithLock(claims.itemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        if (!item.getUserId().equals(claims.userId())) {
            throw new BusinessException(ErrorCode.ITEM_QR_INVALID);
        }
        validateAvailable(item);

        item.use(LocalDateTime.now(clock), shop.getId());
        return UserItemUseResponse.from(item);
    }

    private ItemQrClaims verifyItemQr(String token) {
        try {
            return tokenIssuer.verifyItemQr(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.ITEM_QR_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.ITEM_QR_INVALID);
        }
    }

    private void validateOwner(UserItem item, Long userId) {
        if (!item.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ITEM_FORBIDDEN);
        }
    }

    private void validateAvailable(UserItem item) {
        if (item.getStatus() == UserItemStatus.USED) {
            throw new BusinessException(ErrorCode.ITEM_ALREADY_USED);
        }
        if (item.getStatus() == UserItemStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.ITEM_EXPIRED);
        }
        // 만료일은 inclusive-through-date — 만료일 '당일'까지는 사용 가능(쿠폰/교환권 관례).
        // 예: expiresAt=6/9면 6/9 23:59까지 OK, 6/10부터 만료. 그래서 isBefore(오늘)만 만료 처리.
        if (item.getExpiresAt() != null && item.getExpiresAt().isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.ITEM_EXPIRED);
        }
        if (item.getStatus() != UserItemStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
