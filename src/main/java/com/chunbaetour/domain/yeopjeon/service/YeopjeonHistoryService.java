package com.chunbaetour.domain.yeopjeon.service;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.yeopjeon.dto.response.YeopjeonHistoryResponse;
import com.chunbaetour.domain.yeopjeon.entity.YeopjeonHistory;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class YeopjeonHistoryService {

    private final YeopjeonHistoryRepository yeopjeonHistoryRepository;

    public CursorPageResponse<YeopjeonHistoryResponse> getHistories(Long userId, String cursor, int size) {
        List<YeopjeonHistory> histories = fetchHistories(userId, cursor, size);

        boolean hasNext = histories.size() > size;
        List<YeopjeonHistory> content = hasNext ? histories.subList(0, size) : histories;

        String nextCursor = hasNext ? encodeCursor(content.get(content.size() - 1).getId()) : null;

        List<YeopjeonHistoryResponse> responses = content.stream()
                .map(YeopjeonHistoryResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    private List<YeopjeonHistory> fetchHistories(Long userId, String cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        if (cursor == null) {
            return yeopjeonHistoryRepository.findByUserIdOrderByIdDesc(userId, pageable);
        }
        Long cursorId = decodeCursor(cursor);
        return yeopjeonHistoryRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursorId, pageable);
    }

    // TODO: CursorUtils PR 머지 후 아래 두 메서드를 제거하고 CursorUtils.encode/decode로 교체
    // private String encodeCursor(Long id) {
    //     return CursorUtils.encode(id);
    // }
    // private Long decodeCursor(String cursor) {
    //     return CursorUtils.decode(cursor);
    // }

    private String encodeCursor(Long id) {
        String json = "{\"id\":" + id + "}";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private Long decodeCursor(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String json = new String(decoded, StandardCharsets.UTF_8);
            String value = json.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");
            return Long.parseLong(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor);
        }
    }
}
