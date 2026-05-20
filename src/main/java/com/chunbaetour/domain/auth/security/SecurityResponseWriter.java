package com.chunbaetour.domain.auth.security;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Security 필터 체인에서 ApiResponse 포맷의 JSON 에러 응답을 작성하는 공통 헬퍼.
 *
 * <p>사용처:
 * <ul>
 *   <li>{@link RestAuthenticationEntryPoint} (AUTH_006)</li>
 *   <li>{@link RestAccessDeniedHandler} (AUTH_007)</li>
 *   <li>{@link JwtAuthenticationFilter} (AUTH_002/003/013)</li>
 *   <li>RateLimitFilter (AUTH_014, with Retry-After + X-RateLimit-* 헤더)</li>
 * </ul>
 *
 * <p>본 클래스는 응답 헤더 설정 + JSON 직렬화의 단일 진입점이다. 새로운 필터에서 응답이 필요하면 본 메서드를
 * 사용해 응답 포맷 일관성을 유지한다.
 */
@Component
@RequiredArgsConstructor
public class SecurityResponseWriter {

    private final ObjectMapper objectMapper;

    /**
     * 표준 에러 응답 (헤더 추가 없음).
     */
    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        write(response, code, Map.of());
    }

    /**
     * 추가 응답 헤더와 함께 에러 응답.
     *
     * <p>Rate Limit 응답({@code Retry-After}, {@code X-RateLimit-Limit}, {@code X-RateLimit-Remaining})처럼
     * 표준 헤더가 본문 외에 필요한 경우 사용. 헤더는 status/content-type 설정 전에 적용한다.
     *
     * <p>주의: 호출자가 헤더 값을 안전한 ASCII 문자열로 보장해야 한다 (HTTP 응답 헤더 규약). 본 메서드는
     * 추가 검증 없이 그대로 setHeader 호출하므로, 신뢰할 수 없는 입력은 미리 sanitize 필요.
     *
     * @param response 응답 객체
     * @param code     ErrorCode (HTTP status + body code/message)
     * @param headers  추가 응답 헤더. 빈 Map이면 본 메서드는 표준 write와 동일
     */
    public void write(HttpServletResponse response, ErrorCode code, Map<String, String> headers) throws IOException {
        headers.forEach(response::setHeader);
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code.getCode(), code.getMessage()));
    }
}
