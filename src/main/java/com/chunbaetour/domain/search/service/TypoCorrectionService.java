package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.search.constant.SearchRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 오타 교정 검색 서비스 (KAN-276).
 *
 * <h3>동작 원리</h3>
 * <ol>
 *   <li><b>사전 구축</b>: 서버 시작 시 {@code places.name}, {@code festivals.title} 전체를
 *       Redis 2-gram 역인덱스로 적재한다.
 *       Key: {@code search:typo:places:gram:{gram}} / Value: Redis Set (해당 gram을 포함하는 단어 목록).</li>
 *   <li><b>1단계 — 2-gram 프리필터</b>: 검색어의 2-gram을 추출하여 역인덱스에서 후보 단어를 수집한다.
 *       전수 Levenshtein 비교(O(N×L²))를 수십~수백 건으로 줄이는 핵심 최적화.</li>
 *   <li><b>2단계 — Levenshtein 필터</b>: 후보 중 편집거리 ≤ {@value #MAX_DISTANCE}인 단어를 추려 최소 거리 단어를 반환한다.</li>
 * </ol>
 *
 * <h3>Graceful Degradation</h3>
 * Redis 장애 / 사전 미로드 시 예외를 흡수하고 {@code Optional.empty()}를 반환하여
 * 호출부(SearchService)가 기본 빈 결과를 그대로 내려보낸다.
 *
 * <h3>사전 갱신 정책</h3>
 * 매 1시간마다 {@code @Scheduled}로 사전을 자동 재로드한다.
 * 장소/축제 데이터 변경 즉시 반영이 필요한 경우 {@link #refreshPlaceDictionary()} / {@link #refreshFestivalDictionary()}를 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TypoCorrectionService {

    /** Levenshtein 허용 최대 편집 거리 — 이 값 이하인 경우만 교정 후보로 판단 */
    static final int MAX_DISTANCE = 2;

    /** 사전 키 TTL — 매 1시간마다 자동 갱신되므로 넉넉하게 2시간 설정 */
    private static final Duration DICT_TTL = Duration.ofHours(2);

    /** 2-gram 프리필터 최소 공유 gram 수 — 검색어가 짧을수록 완화 */
    private static final int MIN_GRAM_MATCH = 1;

    private final PlaceRepository placeRepository;
    private final FestivalRepository festivalRepository;
    private final StringRedisTemplate stringRedisTemplate;

    // ──────────────────────────────────────────────────────────────────────────
    // 서버 시작 시 사전 초기화
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 서버 완전 기동 후 사전을 Redis에 적재한다.
     * {@code ApplicationReadyEvent}는 컨텍스트 초기화·DB 연결·트랜잭션 인프라가 모두 준비된 시점에 발행된다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        refreshPlaceDictionary();
        refreshFestivalDictionary();
    }

    /**
     * 관광지명 사전을 매 1시간마다 자동 갱신한다.
     * 신규 장소 추가/수정 시 다음 갱신 주기 전까지 최대 1시간의 반영 지연이 발생할 수 있으나,
     * 오타 교정의 특성상 허용 가능한 수준이다.
     */
    @Scheduled(fixedDelay = 3_600_000L) // 1시간마다
    public void refreshPlaceDictionary() {
        try {
            log.info("[TypoCorrection] 관광지 사전 갱신 시작");
            List<String> names = loadPlaceNames();
            buildDictionary(names, SearchRedisKeys.TYPO_GRAM_PREFIX_PLACES);
            log.info("[TypoCorrection] 관광지 사전 갱신 완료 — 총 {}건", names.size());
        } catch (Exception e) {
            log.warn("[TypoCorrection] 관광지 사전 갱신 실패 — 기존 사전 유지", e);
        }
    }

    /**
     * 축제명 사전을 매 1시간마다 자동 갱신한다.
     */
    @Scheduled(fixedDelay = 3_600_000L) // 1시간마다
    public void refreshFestivalDictionary() {
        try {
            log.info("[TypoCorrection] 축제 사전 갱신 시작");
            List<String> titles = loadFestivalNames();
            buildDictionary(titles, SearchRedisKeys.TYPO_GRAM_PREFIX_FESTIVALS);
            log.info("[TypoCorrection] 축제 사전 갱신 완료 — 총 {}건", titles.size());
        } catch (Exception e) {
            log.warn("[TypoCorrection] 축제 사전 갱신 실패 — 기존 사전 유지", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 오타 교정 공개 API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 관광지 검색어에 가장 가까운 교정 후보를 반환한다.
     *
     * @param keyword 원본 검색어 (정규화된 상태)
     * @return 교정 후보 단어. 없으면 {@code Optional.empty()}
     */
    public Optional<String> findClosestForPlaces(String keyword) {
        return findClosest(keyword, SearchRedisKeys.TYPO_GRAM_PREFIX_PLACES);
    }

    /**
     * 축제 검색어에 가장 가까운 교정 후보를 반환한다.
     *
     * @param keyword 원본 검색어 (정규화된 상태)
     * @return 교정 후보 단어. 없으면 {@code Optional.empty()}
     */
    public Optional<String> findClosestForFestivals(String keyword) {
        return findClosest(keyword, SearchRedisKeys.TYPO_GRAM_PREFIX_FESTIVALS);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 핵심 알고리즘 (package-private — 테스트 가시성)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 2-gram 프리필터 + Levenshtein 거리 기반으로 가장 가까운 후보를 반환한다.
     *
     * <p><b>알고리즘 흐름</b>:
     * <ol>
     *   <li>검색어의 2-gram 집합을 추출한다.</li>
     *   <li>각 gram에 대해 역인덱스에서 후보 단어 집합을 수집한다.</li>
     *   <li>후보 중 Levenshtein ≤ {@value #MAX_DISTANCE}인 단어를 필터링하여 최소 거리 단어를 반환한다.</li>
     * </ol>
     *
     * @param keyword   검색어
     * @param gramPrefix Redis 키 prefix ({@code search:typo:places:gram:} 등)
     * @return 최소 편집거리 후보. 없으면 {@code Optional.empty()}
     */
    Optional<String> findClosest(String keyword, String gramPrefix) {
        if (!StringUtils.hasText(keyword)) {
            return Optional.empty();
        }

        try {
            // 1단계: 2-gram 프리필터로 후보 수집
            Set<String> candidates = collectCandidates(keyword, gramPrefix);
            if (candidates.isEmpty()) {
                return Optional.empty();
            }

            // 2단계: Levenshtein 거리 계산 및 최솟값 선택
            String best = null;
            int bestDist = Integer.MAX_VALUE;

            for (String candidate : candidates) {
                // 동일 단어는 교정 불필요
                if (candidate.equals(keyword)) {
                    return Optional.empty();
                }
                int dist = levenshtein(keyword, candidate);
                if (dist <= MAX_DISTANCE && dist < bestDist) {
                    bestDist = dist;
                    best = candidate;
                }
            }

            return Optional.ofNullable(best);
        } catch (Exception e) {
            log.warn("[TypoCorrection] 오타 교정 실패 — 교정 없이 진행. keywordLength={}", keyword.length(), e);
            return Optional.empty();
        }
    }

    /**
     * 검색어의 2-gram 집합을 추출한 뒤 Redis 역인덱스에서 후보 단어를 수집한다.
     *
     * <p>2-gram이 1개도 없는 1글자 검색어는 단어 전체를 1-gram으로 처리한다 (하나의 글자 자체가 gram).
     *
     * @param keyword   검색어
     * @param gramPrefix Redis 키 prefix
     * @return 후보 단어 집합 (중복 제거됨)
     */
    Set<String> collectCandidates(String keyword, String gramPrefix) {
        List<String> grams = extractGrams(keyword);
        if (grams.isEmpty()) {
            return Set.of();
        }

        Set<String> candidates = new HashSet<>();
        for (String gram : grams) {
            String key = gramPrefix + encodeGramForKey(gram);
            Set<String> members = stringRedisTemplate.opsForSet().members(key);
            if (members != null) {
                candidates.addAll(members);
            }
        }
        return candidates;
    }

    /**
     * 문자열에서 2-gram 목록을 추출한다.
     *
     * <p>예: "경복궁" → ["경복", "복궁"]
     * <p>1글자 문자열은 자기 자신을 1-gram으로 반환한다.
     *
     * @param text 대상 문자열
     * @return gram 목록 (순서 보장)
     */
    List<String> extractGrams(String text) {
        List<String> grams = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return grams;
        }
        // 공백 제거 후 gram 추출 (공백은 의미 없는 경계)
        String stripped = text.replaceAll("\\s+", "");
        if (stripped.length() == 1) {
            grams.add(stripped); // 1글자는 자체 gram
            return grams;
        }
        for (int i = 0; i < stripped.length() - 1; i++) {
            grams.add(stripped.substring(i, i + 2));
        }
        return grams;
    }

    /**
     * 두 문자열 사이의 Levenshtein(편집) 거리를 계산한다.
     *
     * <p>표준 동적 프로그래밍 구현. 시간복잡도 O(n×m), 공간복잡도 O(min(n,m)).
     * 외부 라이브러리(Apache Commons Text) 의존 없이 직접 구현하여 의존성을 최소화한다.
     *
     * @param a 첫 번째 문자열
     * @param b 두 번째 문자열
     * @return 편집 거리 (0이면 동일)
     */
    int levenshtein(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();

        // 공간 최적화: 항상 짧은 문자열을 열(column)로 배치
        if (lenA < lenB) {
            return levenshtein(b, a);
        }

        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];

        // 기저: a가 빈 문자열이면 b의 길이만큼 삽입 필요
        for (int j = 0; j <= lenB; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            for (int j = 1; j <= lenB; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1,    // 삽입
                                 prev[j] + 1),         // 삭제
                        prev[j - 1] + cost            // 대체
                );
            }
            // 다음 행을 위해 배열 교체 (재할당 없이 참조 스왑)
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[lenB];
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 사전 구축 내부 로직
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 단어 목록으로 Redis 2-gram 역인덱스를 구축한다.
     *
     * <p>기존 키를 먼저 삭제(evict)한 뒤 재적재하여 삭제된 장소/축제가 사전에 남지 않도록 한다.
     * 키 단위로 파이프라인을 쓰면 이상적이나 단순성을 위해 단건 SADD 방식으로 구현한다.
     *
     * @param words     단어 목록
     * @param gramPrefix Redis 키 prefix
     */
    private void buildDictionary(List<String> words, String gramPrefix) {
        String lockKey = gramPrefix + "lock";
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, Duration.ofMinutes(5));
        if (Boolean.FALSE.equals(acquired)) {
            log.info("[TypoCorrection] 사전 갱신 Lock 획득 실패 (진행 중) - {}", gramPrefix);
            return;
        }

        try {
            String buildToken = UUID.randomUUID().toString();
            Map<String, String> tempKeysByRealKey = new LinkedHashMap<>();

            for (String word : words) {
                if (!StringUtils.hasText(word)) continue;
                List<String> grams = extractGrams(word);
                for (String gram : grams) {
                    String realKey = gramPrefix + encodeGramForKey(gram);
                    String tempKey = tempKeyForSameSlot(realKey, buildToken);
                    tempKeysByRealKey.put(realKey, tempKey);
                    stringRedisTemplate.opsForSet().add(tempKey, word);
                    stringRedisTemplate.expire(tempKey, DICT_TTL);
                }
            }

            for (Map.Entry<String, String> entry : tempKeysByRealKey.entrySet()) {
                stringRedisTemplate.rename(entry.getValue(), entry.getKey());
            }
            // 기존 사전에만 남은 gram 키는 DICT_TTL 만료 시 자연 삭제된다.
        } finally {
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            stringRedisTemplate.execute(new DefaultRedisScript<>(script, Long.class), List.of(lockKey), lockToken);
        }
    }

    private String tempKeyForSameSlot(String realKey, String buildToken) {
        // Redis Cluster RENAME은 source/dest가 같은 hash slot이어야 하므로 real key 자체를 hash tag로 사용한다.
        return "{" + realKey + "}:temp:" + buildToken;
    }

    /**
     * Encodes Redis hash-tag control characters before a gram is used as a key suffix.
     *
     * <p>Place and festival names are user/admin controlled data. If a raw gram contains
     * {@code {}} characters, the temp-key hash tag used for cluster-safe RENAME can be parsed
     * differently from the destination key and CROSSSLOT can recur. Encoding keeps the key
     * deterministic while preventing input data from becoming Redis hash-tag syntax.
     */
    String encodeGramForKey(String gram) {
        return gram
                .replace("%", "%25")
                .replace("{", "%7B")
                .replace("}", "%7D");
    }

    /**
     * 트랜잭션 내에서 ACTIVE 상태의 장소명을 조회한다.
     */
    @Transactional(readOnly = true)
    public List<String> loadPlaceNames() {
        return placeRepository.findAllActiveNames();
    }

    /**
     * 트랜잭션 내에서 전체 축제 제목을 조회한다.
     */
    @Transactional(readOnly = true)
    public List<String> loadFestivalNames() {
        return festivalRepository.findAllNames();
    }
}
