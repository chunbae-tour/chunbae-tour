package com.chunbaetour.domain.common.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * 보안 감사 로그 이벤트 (KAN-105 Epic KAN-64 S4).
 *
 * <p>구조화 로깅 단위. 6하 원칙 필드(언제/누가/대상/어디서/무엇을/결과)를 표준화하여
 * 외부 log aggregator(ELK/CloudWatch Logs) / SIEM이 일관 인덱싱 가능.
 *
 * <p><b>민감 정보 금지</b>: 비밀번호, JWT/Refresh Token 본문, Cookie 값, Authorization 헤더 원본 등은
 * 본 자료형 어떤 필드에도 담지 않는다 (자료형에 부재 → 누락 사고 원천 차단). 이메일 주소는 actorId
 * 매핑으로 대체. {@code metadata}에도 민감 값 넣지 말 것.
 *
 * @param timestamp     이벤트 발생 시각 (UTC Instant)
 * @param eventType     이벤트 분류 (카탈로그 {@link SecurityAuditEventType})
 * @param actorId       인증된 사용자 ID — 미인증 흐름(회원가입 실패, expired token 등)에서는 {@code null}
 * @param targetUserId  본인 외 대상 ID — admin이 user 정지/role 변경 등에서 사용. 현재는 self 흐름만이라 actor와 동일하거나 null
 * @param ipMasked      마스킹된 클라이언트 IP — {@link com.chunbaetour.domain.common.util.IpMaskUtil}로 변환된 값
 * @param userAgent     User-Agent 헤더 (200자 잘림). null/blank 허용
 * @param outcome       SUCCESS / FAILURE — 외부 SIEM 알람 룰의 1차 분기
 * @param reason        실패 사유 코드 (예: AUTH_001, AUTH_003) — 성공이면 null
 * @param metadata      추가 컨텍스트 (endpoint, role 등). 민감 정보 금지. null이면 빈 맵으로 정규화
 */
public record SecurityAuditEvent(
        Instant timestamp,
        SecurityAuditEventType eventType,
        Long actorId,
        Long targetUserId,
        String ipMasked,
        String userAgent,
        String outcome,
        String reason,
        Map<String, String> metadata
) {

    /** User-Agent 헤더 저장 시 최대 길이. 초과분은 잘림. */
    public static final int USER_AGENT_MAX_LENGTH = 200;

    /** outcome 표준 값. SIEM 룰 호환 위해 대문자 고정. */
    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";

    /**
     * compact 생성자 — 필수 필드 + 정규화.
     *
     * <ul>
     *   <li>{@code timestamp} null → 부팅 누락이 아닌 호출 누락이므로 NPE 대신 명시 검증</li>
     *   <li>{@code eventType} null 금지</li>
     *   <li>{@code outcome}은 표준 값(SUCCESS/FAILURE) 외 허용 안 함 (SIEM 룰 호환성)</li>
     *   <li>{@code metadata} null → 빈 맵으로 정규화 (null 체크 부담 제거)</li>
     *   <li>{@code userAgent} 초과 길이 자동 잘림</li>
     * </ul>
     */
    public SecurityAuditEvent {
        if (timestamp == null) {
            throw new IllegalArgumentException("SecurityAuditEvent.timestamp는 null일 수 없습니다.");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("SecurityAuditEvent.eventType은 null일 수 없습니다.");
        }
        if (outcome == null || (!OUTCOME_SUCCESS.equals(outcome) && !OUTCOME_FAILURE.equals(outcome))) {
            throw new IllegalArgumentException(
                    "SecurityAuditEvent.outcome은 SUCCESS 또는 FAILURE만 허용됩니다. 입력: " + outcome);
        }
        if (userAgent != null && userAgent.length() > USER_AGENT_MAX_LENGTH) {
            userAgent = userAgent.substring(0, USER_AGENT_MAX_LENGTH);
        }
        if (metadata == null || metadata.isEmpty()) {
            metadata = Collections.emptyMap();
        } else {
            // Map.copyOf는 null key/value에서 NPE 발생 — audit 생성 중 예외가 인증 비즈니스 흐름까지 깨지 않도록
            // null entry는 filter로 제거 (CR #2 회귀 가드).
            metadata = metadata.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getValue() != null)
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (a, b) -> b));
        }
    }
}
