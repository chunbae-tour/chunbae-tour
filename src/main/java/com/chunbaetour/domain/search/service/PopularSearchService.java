package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.search.dto.response.PopularSearchResponse;
import com.chunbaetour.domain.search.type.RankingChangeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * 인기 검색어 서비스.
 * <p>
 * Redis ZSet({@code search:ranking})을 1차 데이터 저장소로 사용한다.
 * 검색이 발생할 때마다 {@link #incrementSearchCount(String)}를 호출해 score를 증가시키고,
 * 이 서비스에서는 상위 10개를 조회하여 이전 순위({@code search:ranking:prev})와 비교해 응답을 반환한다.
 * </p>
 *
 * <p>
 * <b>SA 기능 명세서 F-SEARCH-002 동작 방식</b>:
 * <ol>
 *   <li>Redis ZSet {@code ZREVRANGE search:ranking 0 9 WITHSCORES}</li>
 *   <li>이전 순위 {@code search:ranking:prev}와 비교하여 changeType 계산</li>
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

    // ──────────────────────────────────────────────────────────────────────────
    // Redis Key 상수
    // ──────────────────────────────────────────────────────────────────────────

    // 외부 접근을 차단하기 위해 private으로 캡슐화.
    // 단위 테스트에서는 상수 문자열("search:ranking")을 직접 사용하거나 @TestPropertySource로 주입할 것.

    /** 오늘 누적 검색 횟수 ZSet 키 (score 높을수록 인기) */
    private static final String RANKING_KEY      = "search:ranking";

    /** 전일 인기 검색어 스냅샷 ZSet 키 (자정 초기화 직전 백업) */
    private static final String RANKING_PREV_KEY = "search:ranking:prev";

    /** 인기 검색어 상위 노출 개수 */
    private static final int TOP_N = 10;

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
     * <b>정규화 정책</b>: 저장 전 {@code strip()}을 적용하여 앞뒤 공백을 제거한다.
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
    public void incrementSearchCount(String keyword) {
        // null·blank 키워드는 카운팅하지 않는다.
        if (!StringUtils.hasText(keyword)) {
            return;
        }

        // 정규화: 앞뒤 공백 제거 (같은 의미의 다른 입력이 별개의 키로 집계되는 왜곡 방지)
        String normalized = keyword.strip();

        // ZINCRBY search:ranking 1 {keyword} — 원자적 연산, thread-safe
        stringRedisTemplate.opsForZSet().incrementScore(RANKING_KEY, normalized, 1);
        log.debug("[PopularSearch] 검색어 카운트 증가: '{}'", normalized);
    }

    /**
     * 인기 검색어 TOP 10 조회.
     * <p>
     * 1. {@code ZREVRANGE search:ranking 0 9 WITHSCORES}로 현재 순위 조회<br>
     * 2. {@code ZREVRANGE search:ranking:prev 0 9}로 이전 순위 조회 (TOP_N 범위로 제한)<br>
     * 3. 각 키워드별 {@link RankingChangeType} 계산 후 응답 목록 반환
     * </p>
     *
     * <p>
     * <b>Known Limitation (자정 경계 비일관성)</b>: 1번 조회(현재 랭킹)와 2번 조회(이전 랭킹) 사이에
     * {@code resetDailyRanking()}이 실행되면, 이전 랭킹 키({@code search:ranking:prev})에 방금 읽은
     * 현재 랭킹과 동일한 데이터가 들어가 모든 변동 타입이 {@code SAME}으로 잘못 계산될 수 있다.
     * 자정 경계의 수십 ms 창에서만 발생하는 극히 드문 케이스이며, Redis Pipeline으로 두 조회를
     * 원자적으로 묶으면 해소되나 현재 구현 복잡도를 고려해 수용 가능 수준으로 판단한다.
     * </p>
     *
     * @return 인기 검색어 목록 (최대 10건, 순위·검색수·변동타입 포함, 불변 리스트)
     */
    public List<PopularSearchResponse> getPopularKeywords() {

        // 1. 현재 인기 검색어 TOP N 조회 (score 내림차순)
        Set<ZSetOperations.TypedTuple<String>> currentRanking =
                stringRedisTemplate.opsForZSet()
                        .reverseRangeWithScores(RANKING_KEY, 0, TOP_N - 1);

        // 랭킹 데이터가 아직 없는 경우 (초기 상태) 빈 리스트 반환
        if (currentRanking == null || currentRanking.isEmpty()) {
            log.debug("[PopularSearch] 현재 랭킹 데이터 없음. 빈 목록 반환");
            return Collections.emptyList();
        }

        // 2. 이전 순위 맵 구성 (keyword → prev 순위, 1-based)
        //    비교 대상은 전날 TOP_N 이내에 있던 키워드만으로 충분하다.
        Map<String, Integer> prevRankMap = buildPrevRankMap();

        // 3. 응답 목록 조립
        List<PopularSearchResponse> result = new ArrayList<>(TOP_N);
        int currentRank = 1;

        for (ZSetOperations.TypedTuple<String> tuple : currentRanking) {
            String keyword = tuple.getValue();

            // Redis 역직렬화 실패 등으로 keyword가 null인 tuple은 건너뜀.
            // null keyword가 응답에 포함되면 클라이언트 파싱 오류로 이어진다.
            if (!StringUtils.hasText(keyword)) {
                log.warn("[PopularSearch] null 또는 빈 keyword tuple 발견. 건너뜀. rank={}", currentRank);
                // null을 skip하더라도 currentRank는 반드시 소진해야 한다.
                // skip 후 미증가 시 다음 유효 keyword가 동일한 순위 번호를 받아 중복 순위가 발생한다.
                currentRank++;
                continue;
            }

            // ZSet score는 double; 실제 검색 횟수이므로 long으로 변환
            long searchCount = (tuple.getScore() != null)
                    ? tuple.getScore().longValue()
                    : 0L;

            RankingChangeType changeType = determineChangeType(keyword, currentRank, prevRankMap);

            result.add(PopularSearchResponse.of(currentRank, keyword, searchCount, changeType));
            currentRank++;
        }

        log.debug("[PopularSearch] 인기 검색어 조회 완료: {}건", result.size());
        // 호출자가 반환된 리스트를 수정할 수 없도록 불변 래퍼로 감싸 반환한다.
        // 서비스 레이어는 항상 불변 컬렉션을 반환하는 것이 방어적 설계의 기본이다.
        return Collections.unmodifiableList(result);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Scheduler — 자정 일간 랭킹 초기화
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 매일 자정 실행: 오늘 랭킹({@code search:ranking})을 이전 랭킹({@code search:ranking:prev})으로
     * 원자적으로 교체(RENAME)하여 일간 집계를 초기화한다.
     *
     * <p>
     * <b>RENAME을 사용하는 이유</b>: Redis RENAME 명령은 아래 두 작업을 <b>원자적으로</b> 수행한다.
     * <ol>
     *   <li>대상 키({@code search:ranking:prev})가 존재하면 자동으로 삭제(교체)</li>
     *   <li>소스 키({@code search:ranking})를 대상 키로 이름 변경</li>
     * </ol>
     * 따라서 별도의 {@code delete(RANKING_PREV_KEY)} 선행 step이 불필요하며,
     * 두 단계로 쪼갤 경우 오히려 step 사이에서 장애가 발생하면 prev 스냅샷이 소실된다.
     * </p>
     *
     * <p>
     * <b>검색 0건 처리</b>: 오늘 검색이 없어 {@code search:ranking}이 존재하지 않으면
     * {@code search:ranking:prev}를 명시적으로 삭제하여 stale 데이터 잔류를 방지한다.
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
            // 0초 대기(즉시 실패), 10초 후 자동 만료. 
            // 0초 대기는 다른 인스턴스가 이미 진행 중이면 바로 포기함을 의미한다.
            isLocked = lock.tryLock(0, 10, TimeUnit.SECONDS);
            
            if (!isLocked) {
                log.info("[PopularSearch] 이미 다른 인스턴스에서 랭킹 초기화를 진행 중입니다. 스케줄러를 건너뜁니다.");
                return;
            }

            // --- 기존 로직 수행 ---
            boolean success = false;
            try {
                // search:ranking 키 존재 여부 확인
                // [주의] 다중 인스턴스 동시 실행 시, A가 rename한 뒤 B가 hasKey=false로 판단하여 delete해버리는 
                // 심각한 데이터 유실(스냅샷 증발) 문제가 발생할 수 있다. 분산 락이 이를 방지한다.
                Boolean rankingExists = stringRedisTemplate.hasKey(RANKING_KEY);

                if (Boolean.TRUE.equals(rankingExists)) {
                    stringRedisTemplate.rename(RANKING_KEY, RANKING_PREV_KEY);
                    log.info("[PopularSearch] 랭킹 스냅샷 완료: {} → {}", RANKING_KEY, RANKING_PREV_KEY);
                } else {
                    stringRedisTemplate.delete(RANKING_PREV_KEY);
                    log.warn("[PopularSearch] 오늘 검색 없음. 이전 스냅샷({})을 제거하여 stale 데이터 방지.", RANKING_PREV_KEY);
                }

                success = true;

            } catch (Exception e) {
                log.error("[PopularSearch] 일간 랭킹 초기화 중 오류 발생", e);
            }

            long elapsedMs = System.currentTimeMillis() - startTime;

            if (success) {
                log.info("[PopularSearch] 일간 랭킹 초기화 완료. 소요시간={}ms", elapsedMs);
            } else {
                log.warn("[PopularSearch] 일간 랭킹 초기화 실패. 소요시간={}ms — 다음 자정에 재시도됩니다.", elapsedMs);
            }

        } catch (InterruptedException e) {
            log.error("[PopularSearch] 랭킹 초기화 분산 락 획득 중 인터럽트 발생", e);
            Thread.currentThread().interrupt();
        } finally {
            if (isLocked) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.warn("[PopularSearch] 랭킹 초기화 분산 락 해제 실패", e);
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 이전 순위 ZSet에서 keyword → 이전 순위(1-based) 맵을 구성한다.
     * <p>
     * <b>조회 범위</b>: {@code TOP_N}으로 제한한다.
     * 비교에 필요한 것은 전날 TOP 10 이내에 있던 키워드뿐이며,
     * 전체({@code 0, -1}) 조회는 수십만 개 누적 시 메모리 낭비 및 지연 원인이 된다.
     * </p>
     * <p>
     * <b>Trade-off</b>: 전날 11위~N위 키워드가 오늘 TOP 10에 진입하면 {@code NEW}로 표시된다.
     * SA 명세서의 의도(전날 TOP 10 기준 비교)와 일치하는 동작이다.
     * </p>
     *
     * @return keyword를 키, 1-based 이전 순위를 값으로 하는 맵. 스냅샷 없으면 빈 맵.
     */
    private Map<String, Integer> buildPrevRankMap() {
        Set<String> prevKeywords = stringRedisTemplate.opsForZSet()
                .reverseRange(RANKING_PREV_KEY, 0, TOP_N - 1);

        if (prevKeywords == null || prevKeywords.isEmpty()) {
            return Collections.emptyMap(); // 이전 랭킹 없음 → 전부 NEW 처리
        }

        // Java 19+ HashMap.newHashMap: load factor(0.75)를 고려한 초기 용량 자동 계산
        // new HashMap<>(size)는 size개 삽입 시 rehash 발생 가능
        Map<String, Integer> prevRankMap = HashMap.newHashMap(prevKeywords.size());
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

        // 이전 순위에 없던 신규 키워드 (또는 전날 TOP_N 밖이었던 키워드)
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
}
