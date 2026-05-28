package com.chunbaetour.domain.store.service;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.store.dto.response.UserItemResponse;
import com.chunbaetour.domain.store.entity.UserItem;
import com.chunbaetour.domain.store.repository.UserItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 보유 아이템 서비스.
 * 담당 기능: cursor 페이징 기반 보유 아이템 조회.
 */
@Service
@RequiredArgsConstructor
public class UserItemService {

    private final UserItemRepository userItemRepository;

    /** 내 보유 아이템 조회 — cursor keyset 페이징 (id DESC) */
    @Transactional(readOnly = true)
    public CursorPageResponse<UserItemResponse> getMyItems(Long userId, String cursor, int size) {
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
}
