package com.chunbaetour.domain.search.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 검색어 자동완성 전용 캐시 리포지토리.
 * <p>
 * 자동완성에 대한 Redis 캐싱 로직(직렬화, 역직렬화, 예외 격리 등)을 서비스 계층에서 분리하여
 * 인프라 기술 종속성을 최소화하고 유지보수성을 향상시킨다.
 * </p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SuggestCacheRepository {

    private static final String KEY_PREFIX = "search:suggest:";
    private static final Duration TTL_NORMAL = Duration.ofMinutes(5);
    private static final Duration TTL_EMPTY = Duration.ofSeconds(10);
    
    // 일반적인 장소명에 사용되지 않는 ASCII Unit Separator를 구분자로 사용하여 
    // "제주||도" 같은 문자열에 의한 분할 오작동을 방지한다.
    private static final String DELIMITER = "\u001F";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 자동완성 결과 캐시 조회
     *
     * @param prefix 정규화된 검색어 (non-null/non-blank)
     * @return 캐시 Hit 시 결과 리스트(빈 리스트 포함), Miss 또는 조회 실패 시 빈 Optional
     */
    public Optional<List<String>> get(String prefix) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(KEY_PREFIX + prefix.toLowerCase());
            
            // 캐시 Miss
            if (cached == null) {
                return Optional.empty();
            }
            
            // 캐싱된 빈 결과
            if (cached.isEmpty()) {
                return Optional.of(List.of());
            }
            
            // 캐시 Hit
            return Optional.of(List.of(cached.split(DELIMITER)));
        } catch (Exception e) {
            log.warn("[SuggestCacheRepository] 캐시 조회 실패 (DB fallback) - prefix: {}", prefix, e);
            return Optional.empty();
        }
    }

    /**
     * 자동완성 결과 캐시 저장
     *
     * @param prefix 정규화된 검색어
     * @param suggestions 자동완성 결과 리스트
     */
    public void set(String prefix, List<String> suggestions) {
        try {
            String key = KEY_PREFIX + prefix.toLowerCase();
            
            if (suggestions.isEmpty()) {
                // 결과 0건 캐싱: 짧은 TTL로 캐시 스탬피드 방지 및 DB 부하 완화
                stringRedisTemplate.opsForValue().set(key, "", TTL_EMPTY);
                log.debug("[SuggestCacheRepository] 빈 자동완성 결과 캐시 저장 - prefix: {}", prefix);
            } else {
                stringRedisTemplate.opsForValue().set(key, String.join(DELIMITER, suggestions), TTL_NORMAL);
                log.debug("[SuggestCacheRepository] 자동완성 캐시 저장 - prefix: {}, count: {}", prefix, suggestions.size());
            }
        } catch (Exception e) {
            // 캐시 저장 실패는 비즈니스 응답에 영향을 주지 않아야 함 (예외 격리)
            log.warn("[SuggestCacheRepository] 자동완성 캐시 저장 실패 (무시) - prefix: {}", prefix, e);
        }
    }
}
