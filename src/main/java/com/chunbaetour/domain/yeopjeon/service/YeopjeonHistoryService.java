package com.chunbaetour.domain.yeopjeon.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
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

/**
 * 엽전 사용 내역 cursor 페이징 서비스.
 * cursor 형식: {"id":N} → Base64URL 인코딩 (padding 없음).
 * CursorUtils PR 머지 후 encode/decode를 공통 유틸로 교체 예정.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class YeopjeonHistoryService {

    private final YeopjeonHistoryRepository yeopjeonHistoryRepository;

    /**
     * 엽전 사용 내역을 cursor 기반으로 페이징 조회.
     * <p>
     * size+1개를 요청해 결과가 size 초과이면 다음 페이지가 있다고 판단(hasNext=true).
     * nextCursor는 실제 반환 목록의 마지막 항목 id를 인코딩한 값.
     * cursor가 null이면 첫 페이지(최신순 전체), 있으면 cursor id 이전 항목부터 조회.
     * </p>
     */
    public CursorPageResponse<YeopjeonHistoryResponse> getHistories(Long userId, String cursor, int size) {
        List<YeopjeonHistory> histories = fetchHistories(userId, cursor, size);

        // size+1 요청 결과로 다음 페이지 존재 여부 판단
        boolean hasNext = histories.size() > size;
        List<YeopjeonHistory> content = hasNext ? histories.subList(0, size) : histories;

        // 다음 페이지 있으면 마지막 항목 id를 cursor로 인코딩
        String nextCursor = hasNext ? encodeCursor(content.get(content.size() - 1).getId()) : null;

        List<YeopjeonHistoryResponse> responses = content.stream()
                .map(YeopjeonHistoryResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    /**
     * cursor 유무에 따라 조회 쿼리 분기.
     * cursor 없음 → 첫 페이지(전체 최신순).
     * cursor 있음 → cursor id보다 작은 id 목록 (keyset 페이징, OFFSET 미사용).
     */
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

    // cursor 형식: {"id":N} → Base64URL 인코딩 (padding 없음, URL-safe)
    private String encodeCursor(Long id) {
        String json = "{\"id\":" + id + "}";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    // 잘못된 cursor 전달 시 COMMON_002(INVALID_REQUEST) 반환
    private Long decodeCursor(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String json = new String(decoded, StandardCharsets.UTF_8);
            String value = json.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");
            return Long.parseLong(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
