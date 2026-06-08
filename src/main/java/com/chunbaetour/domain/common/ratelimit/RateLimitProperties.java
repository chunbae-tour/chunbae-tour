package com.chunbaetour.domain.common.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate Limit 정책 yml 설정 바인딩.
 *
 * <p>적용 prefix: {@code ratelimit}
 *
 * <pre>
 * ratelimit:
 *   enabled: true
 *   endpoints:
 *     - id: signup
 *       method: POST
 *       path: /api/v1/users/auth/signup
 *       limit: 3
 *       window: PT10M
 *     - id: user-login
 *       method: POST
 *       path: /api/v1/users/auth/login
 *       limit: 5
 *       window: PT1M
 *     ...
 * </pre>
 *
 * <p>설계 결정:
 * <ul>
 *   <li><b>List 구조</b>: Map 대신 List를 사용해 매칭 순서를 yml 작성자가 명시적으로 통제 (구체적 패턴 위, 일반 패턴 아래).
 *       Spring Security URL 매핑 규칙과 동일한 멘탈 모델.</li>
 *   <li><b>id 필수</b>: 키 prefix {@code ratelimit:{id}:{ip}}에 들어가는 식별자. endpoint별 namespace 분리로 키 충돌 차단.</li>
 *   <li><b>enabled 토글</b>: 로컬 개발 시 yml 한 줄로 비활성화 가능. 운영 환경 변수로도 오버라이드 가능.</li>
 * </ul>
 *
 * <p>compact constructor에서 endpoint 목록 null 정규화와 id 중복 검증을 강제해 yml 설정 오류를 부팅 시점에 차단한다.
 *
 * @param enabled   전역 활성화 토글. false면 RateLimitFilter가 모든 요청을 즉시 통과.
 * @param endpoints 정책 리스트. 빈 리스트도 허용 (정책 없음 = 사실상 비활성화)
 */
@ConfigurationProperties(prefix = "ratelimit")
public record RateLimitProperties(boolean enabled, List<EndpointPolicy> endpoints) {

    public RateLimitProperties {
        // null safety — endpoints 미설정 시 빈 리스트로 정규화
        if (endpoints == null) {
            endpoints = List.of();
        } else {
            // 불변 복사로 외부 mutation 방지
            endpoints = List.copyOf(endpoints);
        }
        // id 중복 검증 — 두 정책이 같은 id를 가지면 키 충돌 + 정책 우선순위 모호
        long distinctIds = endpoints.stream().map(EndpointPolicy::id).distinct().count();
        if (distinctIds != endpoints.size()) {
            throw new IllegalStateException(
                    "ratelimit.endpoints에 중복된 id가 있습니다. id는 유일해야 합니다.");
        }
    }

    /**
     * 단일 endpoint 정책.
     *
     * <p>URL 매칭 규칙:
     * <ul>
     *   <li>{@code method}: HTTP 메서드 (대소문자 무관, 필수). 비어 있으면 IllegalArgumentException.</li>
     *   <li>{@code path}: 정확한 URI 매칭. 패턴 매칭 필요 시 추후 AntPathMatcher 도입.</li>
     * </ul>
     *
     * @param id     키 prefix용 식별자 (signup, user-login 등 — 영문 소문자 + 하이픈 권장)
     * @param method HTTP 메서드 (POST 등)
     * @param path   정확한 URI 매칭 대상
     * @param limit  허용 횟수
     * @param window 시간 창 (1초 이상)
     */
    public record EndpointPolicy(String id, String method, String path, int limit, Duration window) {

        public EndpointPolicy {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("EndpointPolicy.id는 비어 있을 수 없습니다.");
            }
            if (method == null || method.isBlank()) {
                throw new IllegalArgumentException("EndpointPolicy.method는 비어 있을 수 없습니다. id=" + id);
            }
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("EndpointPolicy.path는 비어 있을 수 없습니다. id=" + id);
            }
            new RateLimitPolicy(limit, window);  // limit/window 계약을 바인딩 시점에 검증 (반환값 미사용 — 검증 부수효과만 활용)
        }

        /**
         * yml 설정을 도메인 정책 객체로 변환. limit/window 검증은 {@link RateLimitPolicy}가 책임.
         */
        public RateLimitPolicy toPolicy() {
            return new RateLimitPolicy(limit, window);
        }
    }
}
