package com.chunbaetour.domain.common.util;

/**
 * 로그/감사 출력용 IP 마스킹 유틸.
 *
 * <p>PII 정책: 운영 로그에 raw IP 전체를 남기지 않는다. 운영 추적성(같은 /24 대역 패턴 감지)은
 * 유지하면서 마지막 옥텟/그룹만 가리는 절충안.
 *
 * <ul>
 *   <li>IPv4: {@code 192.168.0.1} → {@code 192.168.0.***}</li>
 *   <li>IPv6: {@code 2001:db8::8a2e:370:7334} → {@code 2001:db8::8a2e:370:***}</li>
 *   <li>null/blank: {@code ***}</li>
 * </ul>
 *
 * <p>KAN-65 RateLimitFilter에 처음 도입된 패턴을 KAN-105 보안 감사 로그에서 재사용 위해 공통화.
 * 마스킹 강도(예: SHA-256 해시)는 cross-request 동일 IP 추적이 필요해질 때 후속 검토.
 */
public final class IpMaskUtil {

    private IpMaskUtil() {
        // 유틸 클래스 인스턴스화 금지
    }

    /**
     * 로그용 IP 마스킹.
     *
     * @param ip 원본 IP (IPv4/IPv6 문자열). null/blank 허용
     * @return 마스킹된 문자열. 입력이 비정상이면 {@code ***}
     */
    public static String mask(String ip) {
        if (ip == null || ip.isBlank()) {
            return "***";
        }
        // loopback / link-local — 운영 모니터링/디버깅 식별성 우선해 원본 보존 (PII 위험 0).
        if ("127.0.0.1".equals(ip) || "::1".equals(ip) || ip.startsWith("fe80:")) {
            return ip;
        }
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) {
            return ip.substring(0, lastDot) + ".***";
        }
        // IPv6 — 마지막 그룹만 마스킹. `2001:db8::8a2e:370:7334` → `2001:db8::8a2e:370:***`
        int lastColon = ip.lastIndexOf(':');
        if (lastColon > 0) {
            return ip.substring(0, lastColon) + ":***";
        }
        return "***";
    }
}
