package com.chunbaetour.domain.common.util;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * cursor 기반 페이지네이션 인코딩/디코딩 유틸.
 *
 * <p>cursor 형식: id(Long) → Base64URL 인코딩 (padding 없음, URL-safe).
 *
 * <p>메서드 선택 기준:
 * <ul>
 *   <li>{@link #encode} — id를 cursor 문자열로 변환. 응답 생성 시 사용.</li>
 *   <li>{@link #decode} — cursor 문자열을 id로 변환. 유효하지 않으면 {@link IllegalArgumentException}.
 *       직접 예외를 핸들링할 때 사용.</li>
 *   <li>{@link #decodeSafe} — cursor 문자열을 id로 변환. null이면 null 반환(첫 페이지),
 *       유효하지 않으면 {@link com.chunbaetour.domain.common.error.BusinessException}(INVALID_CURSOR).
 *       컨트롤러/서비스에서 클라이언트 입력을 처리할 때 사용.</li>
 * </ul>
 *
 * <p><b>일반적인 사용 패턴:</b>
 * <pre>{@code
 * // 응답: 마지막 항목의 id를 cursor로 인코딩
 * String nextCursor = CursorUtils.encode(lastItem.getId());
 *
 * // 요청: 클라이언트가 전달한 cursor 디코딩 (null = 첫 페이지)
 * Long cursorId = CursorUtils.decodeSafe(cursor);
 * }</pre>
 */
public final class CursorUtils {

    private CursorUtils() {}

    /** id를 Base64URL cursor 문자열로 인코딩 */
    public static String encode(long id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(id).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64URL cursor 문자열을 id(long)로 디코딩.
     * 유효하지 않은 cursor이면 {@link IllegalArgumentException}.
     * null 입력 시 NPE — null 처리가 필요하면 {@link #decodeSafe} 사용.
     */
    public static long decode(String cursor) {
        try {
            long id = Long.parseLong(new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (id < 1L) {
                throw new IllegalArgumentException("Invalid cursor: " + cursor);
            }
            return id;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor);
        }
    }

    /**
     * Base64URL cursor 문자열을 id(Long)로 디코딩. 서비스/컨트롤러 전용.
     * <ul>
     *   <li>null → null 반환 (첫 페이지 요청으로 처리)</li>
     *   <li>유효하지 않은 cursor → {@link com.chunbaetour.domain.common.error.BusinessException}(INVALID_CURSOR)
     *       — 클라이언트에 의미있는 에러코드 응답 가능</li>
     * </ul>
     * decode()와 달리 null 체크 + 비즈니스 예외 변환을 내장하므로 서비스에서 별도 처리 불필요.
     */
    public static Long decodeSafe(String cursor) {
        if (cursor == null) return null;
        try {
            return decode(cursor);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
