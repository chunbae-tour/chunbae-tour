package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.search.dto.response.PopularSearchResponse;
import com.chunbaetour.domain.search.type.RankingChangeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.chunbaetour.domain.search.constant.SearchRedisKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import java.util.concurrent.TimeUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 인기 검색어 서비스.
 * <p>
 * Redis ZSet({@code search:ranking})을 1차 데이터 저장소로 사용한다.
 * 검색이 발생할 때마다 {@link incrementSearchCount(String)}를 호출해 score를 증가시키고,
 * 이 서비스에서는 상위 N개를 조회하여 이전 순위({@code {search:ranking}:prev})와 비교해 응답을 반환한다.
 * </p>
 *
 * <p>
 * <b>SA 기능 명세서 F-SEARCH-002 동작 방식</b>:
 * <ol>
 *   <li>Redis ZSet {@code ZREVRANGE search:ranking 0 N WITHSCORES}</li>
 *   <li>이전 순위 {@code {search:ranking}:prev}와 비교하여 changeType 계산</li>
 * </ol>
 * </p>
 *
 * <p>
 * <b>스케줄러</b>: 매일 자정({@code search.ranking.reset-cron})에 오늘 랭킹을 RENAME으로 prev로
 * 원자적 교체한 뒤 오늘 랭킹을 초기화한다.
 * </p>
 *
 * <p>
 * <b>다중 인스턴스 대응 (분산 락)</b>: 여러 인스턴스에서 스케줄러가 동시 실행되면
 * 하나가 RENAME한 직후 다른 하나가 키 없음을 보고 DELETE를 수행해 스냅샷이 유실될 수 있다.
 * 이를 방지하기 위해 Redisson 분산 락({@code RLock})을 스케줄러 진입 시점에 적용하여
 * 단일 인스턴스만 초기화를 수행하도록 보장한다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PopularSearchService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    @Value("${search.ranking.top-n:10}")
    private int topN;

    // ──────────────────────────────────────────────────────────────────────────
    // Redis Key 상수
    // ──────────────────────────────────────────────────────────────────────────

    /** 스케줄러 분산 락 만료 시간 (Watchdog 도입으로 상수 제거) */

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 검색어 카운트 증가.
     * <p>
     * Phase 2-2(관광지 검색) / 2-3(축제 검색)의 {@code SearchService}에서 검색 실행 시 호출한다.
     * Redis ZSet {@code ZINCRBY search:ranking 1 {keyword}} 로 score를 원자적으로 1 증가시킨다.
     * </p>
     *
     * <p>
     * <b>정규화 정책</b>: 저장 전 {@link #normalize(String)}을 적용하여 앞뒤 공백을 제거한다.
     * " 제주도", "제주도 ", "제주도" 가 모두 동일한 키로 집계되어 왜곡을 방지한다.
     * 빈 문자열이거나 null이면 카운트하지 않는다.
     * </p>
     *
     * <p>
     * <b>키워드 유효성</b>: 호출 측(SearchService)에서 {@code PLACE_005}(최소 1자),
     * {@code PLACE_006}(최대 50자) 검증을 통과한 키워드만 전달해야 한다.
     * </p>
     *
     * @param keyword 검색어 (null·blank 허용 — 내부에서 무시됨)
     */
    public void incrementSearchCount(String keyword, String clientIp) {
        // null·blank 키워드는 카운팅하지 않는다.
        if (!StringUtils.hasText(keyword)) {
            return;
        }

        // 정규화: 앞뒤 공백 제거
        String normalized = normalize(keyword);

        try {
            // 매크로 어뷰징 방어 (IP 기반 Deduplication)
            // 10분 내에 동일 IP에서 동일 검색어를 검색한 이력이 있다면 집계하지 않는다.
            // 개인정보 보호를 위해 IP는 강력한 단방향 해싱(SHA-256)하여 저장한다.
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest((clientIp + "chunbae-salt").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            String hashedIp = sb.toString();
            String deduplicationKey = "search:log:" + hashedIp + ":" + normalized;

            Boolean isFirstSearch = stringRedisTemplate.opsForValue().setIfAbsent(deduplicationKey, "1", 10, TimeUnit.MINUTES);

            if (Boolean.TRUE.equals(isFirstSearch)) {
                // ZINCRBY search:ranking 1 {keyword} — 원자적 연산, thread-safe
                stringRedisTemplate.opsForZSet().incrementScore(SearchRedisKeys.POPULAR_RANKING_KEY, normalized, 1);
                log.debug("[PopularSearch] 검색어 카운트 증가: '{}'", normalized);
            } else {
                log.debug("[PopularSearch] 중복 검색(어뷰징) 차단: '{}'", normalized);
            }
        } catch (Exception e) {
            // 집계 실패는 검색 서비스 전체를 중단시키지 않는다
            log.warn("[PopularSearch] 검색어 카운트 증가 실패. keyword='{}', error={}", normalized, e.getMessage());
        }
    }

    /**
     * 인기 검색어 상위 N 조회.
     * <p>
     * 1. {@code ZREVRANGE search:ranking 0 N WITHSCORES}로 현재 순위 조회<br>
     * 2. {@code ZREVRANGE {search:ranking}:prev 0 N}로 이전 순위 조회 (topN 범위로 제한)<br>
     * 3. 각 키워드별 {@link RankingChangeType} 계산 후 응답 목록 반환
     * </p>
     *
     * <p>
     * <b>Known Limitation (자정 경계 비일관성)</b>: 1번 조회(현재 랭킹)와 2번 조회(이전 랭킹) 사이에
     * {@code resetDailyRanking()}이 실행되면, 이전 랭킹 키({@code {search:ranking}:prev})에 방금 읽은
     * 현재 랭킹과 동일한 데이터가 들어가 모든 변동 타입이 {@code SAME}으로 잘못 계산될 수 있다.
     * 자정 경계의 수십 ms 창에서만 발생하는 극히 드문 케이스이며, Redis Pipeline으로 두 조회를
     * 원자적으로 묶으면 해소되나 현재 구현 복잡도를 고려해 수용 가능 수준으로 판단한다.
     * </p>
     *
     * @return 인기 검색어 목록 (최대 topN건, 순위·검색수·변동타입 포함, 불변 리스트)
     */
    public List<PopularSearchResponse> getPopularKeywords() {
        try {
            // 1. 현재 인기 검색어 TOP N 조회 (score 내림차순)
            Set<ZSetOperations.TypedTuple<String>> currentRanking =
                    stringRedisTemplate.opsForZSet()
                            .reverseRangeWithScores(SearchRedisKeys.POPULAR_RANKING_KEY, 0, topN - 1);

            // 랭킹 데이터가 아직 없는 경우 (초기 상태) 빈 리스트 반환
            if (currentRanking == null || currentRanking.isEmpty()) {
                log.debug("[PopularSearch] 현재 랭킹 데이터 없음. 빈 목록 반환");
                return Collections.emptyList();
            }

            // 2. 이전 순위 맵 구성 (keyword → prev 순위, 1-based)
            //    비교 대상은 전날 topN 이내에 있던 키워드만으로 충분하다.
            Map<String, Integer> prevRankMap = buildPrevRankMap();

            // 3. 응답 목록 조립
            List<PopularSearchResponse> result = new ArrayList<>(topN);
            int currentRank = 1;

            for (ZSetOperations.TypedTuple<String> tuple : currentRanking) {
                String keyword = tuple.getValue();

                // Redis 역직렬화 실패 등으로 keyword가 null인 tuple은 건너뜀.
                // null keyword가 응답에 포함되면 클라이언트 파싱 오류로 이어진다.
                if (!StringUtils.hasText(keyword)) {
                    log.warn("[PopularSearch] null 또는 빈 keyword tuple 발견. 건너뜀. rank={}", currentRank);
                    // 유효하지 않은 데이터를 skip하고 유효한 항목만으로 TOP N을 채우기 위해 currentRank를 증가시키지 않는다.
                    // 이 경우 Redis에서 가져온 N+1번째 데이터가 N위로 올라오게 되지만, 유효한 검색어만 보여주는 것이 더 중요하다.
                    continue;
                }

                // ZSet score는 double; 실제 검색 횟수이므로 long으로 변환
                long searchCount = (tuple.getScore() != null)
                        ? tuple.getScore().longValue()
                        : 0L;

                RankingChangeType changeType = determineChangeType(keyword, currentRank, prevRankMap);

                result.add(new PopularSearchResponse(currentRank, keyword, searchCount, changeType));
                currentRank++;
            }

            log.debug("[PopularSearch] 인기 검색어 조회 완료: {}건", result.size());
            // 호출자가 반환된 리스트를 수정할 수 없도록 불변 래퍼로 감싸 반환한다.
            // 서비스 레이어는 항상 불변 컬렉션을 반환하는 것이 방어적 설계의 기본이다.
            return Collections.unmodifiableList(result);

        } catch (Exception e) {
            log.error("[PopularSearch] 인기 검색어 조회 실패", e);
            // 조회 실패 시 500 에러를 뱉기보다는 빈 리스트를 반환하여 사용자 경험을 보호한다.
            return Collections.emptyList();
        }
    }

    /**
     * 자동완성 시 보완할 인기 검색어 목록을 조회한다.
     * <p>
     * [Known Limitation] 데이터가 많을 경우 ZSet 전체 스캔 성능 저하 위험이 있다.
     * 이를 방지하기 위해 상위 100위({@code 0, 99})까지만 읽어 필터링한다.
     * 향후 전체 자동완성을 위해서는 Trie 구조나 ZRANGEBYLEX 등을 도입하는 아키텍처 개선이 필요하다.
     * </p>
     *
     * @param prefix 사용자가 입력한 검색어 접두사
     * @param limit 최대 반환 건수
     * @return 인기 검색어 중 prefix로 시작하는 키워드 목록
     */
    public List<String> fetchPopularSuggestions(String prefix, int limit) {
        if (!StringUtils.hasText(prefix) || limit <= 0) {
            return Collections.emptyList();
        }
        try {
            Set<ZSetOperations.TypedTuple<String>> rankingSet =
                    stringRedisTemplate.opsForZSet().reverseRangeWithScores(
                            SearchRedisKeys.POPULAR_RANKING_KEY, 0, 99);

            if (rankingSet == null || rankingSet.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> matched = new ArrayList<>();
            String lowerPrefix = prefix.toLowerCase();
            for (ZSetOperations.TypedTuple<String> tuple : rankingSet) {
                String keyword = tuple.getValue();
                if (keyword != null && keyword.toLowerCase().startsWith(lowerPrefix)) {
                    matched.add(keyword);
                    if (matched.size() >= limit) {
                        break;
                    }
                }
            }
            return matched;
        } catch (Exception e) {
            log.warn("[PopularSearchService] Redis ZSet 자동완성 조회 실패 (무시) - prefix: {}", prefix, e);
            return Collections.emptyList();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scheduler — 자정 일간 랭킹 초기화
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 매일 자정 실행: 오늘 랭킹({@code search:ranking})을 이전 랭킹({@code {search:ranking}:prev})으로
     * 원자적으로 교체(RENAME)하여 일간 집계를 초기화한다.
     *
     * <p>
     * <b>RENAME을 사용하는 이유</b>: Redis RENAME 명령은 아래 두 작업을 <b>원자적으로</b> 수행한다.
     * <ol>
     *   <li>대상 키({@code {search:ranking}:prev})가 존재하면 자동으로 삭제(교체)</li>
     *   <li>소스 키({@code search:ranking})를 대상 키로 이름 변경</li>
     * </ol>
     * 따라서 별도의 {@code delete(RANKING_PREV_KEY)} 선행 step이 불필요하며,
     * 두 단계로 쪼갤 경우 오히려 step 사이에서 장애가 발생하면 prev 스냅샷이 소실된다.
     * </p>
     *
     * <p>
     * <b>검색 0건 처리</b>: 오늘 검색이 없어 {@code search:ranking}이 존재하지 않으면
     * {@code {search:ranking}:prev}를 명시적으로 삭제하여 stale 데이터 잔류를 방지한다.
     * (prev가 없으면 다음 날 전 키워드가 {@code NEW}로 표시 — 이것이 올바른 동작)
     * </p>
     *
     * <p>
     * <b>cron 설정</b>: {@code application.yml}의 {@code search.ranking.reset-cron}으로 외부화.
     * 재배포 없이 초기화 주기를 변경할 수 있다.
     * </p>
     */
    @Scheduled(cron = "${search.ranking.reset-cron:0 0 0 * * *}")
    public void resetDailyRanking() {
        log.info("[PopularSearch] 일간 랭킹 초기화 시작 (분산 락 획득 시도)");

        // 분산 락: 다중 인스턴스 환경에서 동시 실행 방지
        RLock lock = redissonClient.getLock("lock:search:ranking:reset");
        boolean isLocked = false;
        long startTime = System.currentTimeMillis();

        try {
            // 0초 대기(즉시 실패). leaseTime을 지정하지 않으면 Redisson Watchdog이 동작하여
            // 락을 쥔 스레드가 살아있는 한 30초 단위로 자동 연장하므로 중간에 풀릴 위험이 없습니다.
            isLocked = lock.tryLock(0, TimeUnit.SECONDS);

            if (!isLocked) {
                log.info("[PopularSearch] 이미 다른 인스턴스에서 랭킹 초기화를 진행 중입니다. 스케줄러를 건너뜁니다.");
                return;
            }

            try {
                // 실제 초기화 로직 수행
                doResetDailyRanking();

                long elapsedMs = System.currentTimeMillis() - startTime;
                log.info("[PopularSearch] 일간 랭킹 초기화 완료. 소요시간={}ms", elapsedMs);
            } catch (Exception e) {
                long elapsedMs = System.currentTimeMillis() - startTime;
                // 초기화 실패는 운영에 중대한 영향(변동 타입 오작동)을 미치므로 ERROR 레벨 로깅
                log.error("[PopularSearch] 일간 랭킹 초기화 실패. 소요시간={}ms — 다음 자정에 재시도됩니다.", elapsedMs, e);
            }

        } catch (InterruptedException e) {
            log.error("[PopularSearch] 랭킹 초기화 분산 락 획득 중 인터럽트 발생", e);
            Thread.currentThread().interrupt();
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.warn("[PopularSearch] 랭킹 초기화 분산 락 해제 실패", e);
                }
            }
        }
    }

    /**
     * 성공/실패 여부를 분리하여 실제 내부 로직의 흐름을 명확하게 처리한다.
     */
    private void doResetDailyRanking() {
        // search:ranking 키 존재 여부 확인
        // [주의] 다중 인스턴스 동시 실행 시, A가 rename한 뒤 B가 hasKey=false로 판단하여 delete해버리는
        // 심각한 데이터 유실(스냅샷 증발) 문제가 발생할 수 있다. 분산 락이 이를 방지한다.
        Boolean rankingExists = stringRedisTemplate.hasKey(SearchRedisKeys.POPULAR_RANKING_KEY);

        if (Boolean.TRUE.equals(rankingExists)) {
            // RENAME: search:ranking → {search:ranking}:prev
            // - 원자적 연산: prev가 이미 존재하면 자동 교체 (별도 delete 불필요)
            // - 실행 후 search:ranking 키는 소멸 → 당일 집계 초기화 완료
            stringRedisTemplate.rename(SearchRedisKeys.POPULAR_RANKING_KEY, SearchRedisKeys.POPULAR_RANKING_PREV_KEY);
            log.info("[PopularSearch] 랭킹 스냅샷 완료: {} → {}", SearchRedisKeys.POPULAR_RANKING_KEY, SearchRedisKeys.POPULAR_RANKING_PREV_KEY);
        } else {
            // 오늘 검색이 한 건도 없었던 경우, 이전 랭킹 기준이 무의미하므로 스냅샷을 제거한다.
            // (다음 날 조회 시 이전 랭킹이 없어져 모두 NEW로 표시되도록 하는 올바른 설계 동작)
            stringRedisTemplate.delete(SearchRedisKeys.POPULAR_RANKING_PREV_KEY);
            log.warn("[PopularSearch] 오늘 검색 없음. 이전 스냅샷({})을 제거하여 stale 데이터 방지.", SearchRedisKeys.POPULAR_RANKING_PREV_KEY);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 이전 순위 ZSet에서 keyword → 이전 순위(1-based) 맵을 구성한다.
     * <p>
     * <b>조회 범위</b>: {@code topN}으로 제한한다.
     * 비교에 필요한 것은 전날 TOP N 이내에 있던 키워드뿐이며,
     * 전체({@code 0, -1}) 조회는 수십만 개 누적 시 메모리 낭비 및 지연 원인이 된다.
     * </p>
     * <p>
     * <b>Trade-off</b>: 전날 N위권 밖이었던 키워드가 오늘 TOP N에 진입하면 {@code NEW}로 표시된다.
     * SA 명세서의 의도(전날 TOP N 기준 비교)와 일치하는 동작이다.
     * </p>
     *
     * @return keyword를 키, 1-based 이전 순위를 값으로 하는 맵. 스냅샷 없으면 빈 맵.
     */
    private Map<String, Integer> buildPrevRankMap() {
        Set<String> prevKeywords = stringRedisTemplate.opsForZSet()
                .reverseRange(SearchRedisKeys.POPULAR_RANKING_PREV_KEY, 0, topN - 1);

        if (prevKeywords == null || prevKeywords.isEmpty()) {
            return Collections.emptyMap(); // 이전 랭킹 없음 → 전부 NEW 처리
        }

        // load factor(0.75)를 고려한 초기 용량 계산: (size / 0.75) + 1
        Map<String, Integer> prevRankMap = new HashMap<>((int) (prevKeywords.size() / 0.75) + 1);
        int rank = 1;
        for (String keyword : prevKeywords) {
            prevRankMap.put(keyword, rank++);
        }
        return prevRankMap;
    }

    /**
     * 현재 순위와 이전 순위를 비교하여 {@link RankingChangeType}을 결정한다.
     *
     * @param keyword     비교 대상 검색어 (non-null 보장: 호출 전 검증 완료)
     * @param currentRank 현재 순위 (1-based)
     * @param prevRankMap 이전 순위 맵 (keyword → 1-based 이전 순위)
     * @return UP, DOWN, SAME, NEW 중 하나
     */
    private RankingChangeType determineChangeType(
            String keyword,
            int currentRank,
            Map<String, Integer> prevRankMap
    ) {
        Integer prevRank = prevRankMap.get(keyword);

        // 이전 순위에 없던 신규 키워드 (또는 전날 topN 밖이었던 키워드)
        if (prevRank == null) {
            return RankingChangeType.NEW;
        }

        // 순위 비교: 숫자가 작을수록 높은 순위 (1위 > 2위)
        if (currentRank < prevRank) {
            return RankingChangeType.UP;
        } else if (currentRank > prevRank) {
            return RankingChangeType.DOWN;
        } else {
            return RankingChangeType.SAME;
        }
    }

    /**
     * 향후 정규화 정책 변경(예: 소문자 통일 등) 시 확장을 용이하게 하기 위한 키워드 정규화 메서드.
     * XSS 방어(태그 이스케이프)는 출력(렌더링) 시점에 수행하는 것이 표준이므로 여기서는 제외한다.
     */
    private String normalize(String keyword) {
        return keyword.strip();
    }
}
