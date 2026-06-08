package com.chunbaetour.domain.common.ratelimit;

/**
 * Rate Limit 적용 진입점 인터페이스.
 *
 * <p>구현체는 키 단위로 정책({@link RateLimitPolicy})을 강제하고 결과({@link RateLimitDecision})를 반환한다.
 * 호출자는 키 구성과 정책 매핑만 책임지고, 저장소/알고리즘은 구현체에 위임한다.
 *
 * <p>현재 유일한 운영 구현은 {@code RedisRateLimiter} (Fixed Window + Lua atomic). 단위 테스트에서
 * 인메모리 Fake를 만들어 대체할 수 있도록 인터페이스로 분리한다.
 *
 * <p>키 컨벤션 (호출자 책임): {@code ratelimit:{endpoint-id}:{ip}} 형식 권장. endpoint별 namespace 분리로
 * 키 충돌 방지.
 */
public interface RateLimiter {

    /**
     * 키 + 정책으로 1개 토큰을 소비 시도한다.
     *
     * <p>구현체는:
     * <ol>
     *   <li>키의 현재 카운터를 1 증가시키고</li>
     *   <li>카운터가 {@code policy.limit()}을 초과하면 거부({@code allowed=false}) 반환</li>
     *   <li>그렇지 않으면 허용({@code allowed=true}) 반환</li>
     * </ol>
     *
     * @param key    rate limit 카운터 키 (호출자가 endpoint:ip 등으로 조합)
     * @param policy 적용할 정책 (limit + window)
     * @return 판정 결과 (allowed/remaining/retryAfter)
     *
     * <p><b>예외 처리 계약</b>:
     * 본 메서드의 구현체는 인프라 예외(Redis 연결 실패, 타임아웃 등)를 외부로 전파하지 않는다.
     * 모든 인프라 장애는 {@link RateLimitDecision#denied} 응답으로 변환한다 (fail-closed).
     * 호출자({@link RateLimitFilter})가 try/catch 없이 호출할 수 있도록 보장하기 위함이며,
     * 이 contract를 깨뜨리면 rate limit 필터가 500으로 응답해 서비스 가용성에 영향이 간다.
     * {@code RedisRateLimiter}는 본 계약을 {@code DataAccessException} catch로 구현한다.
     */
    RateLimitDecision tryConsume(String key, RateLimitPolicy policy);
}
