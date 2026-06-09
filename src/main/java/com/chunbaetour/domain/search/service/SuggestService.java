package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.KakaoLocalApiClient;
import com.chunbaetour.domain.place.dto.KakaoKeywordResponse;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.search.dto.response.SuggestResponse;
import com.chunbaetour.domain.search.repository.SuggestCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 검색어 자동완성 전용 서비스 (Phase 2-4).
 * <p>
 * SearchService의 역할 비대화(검색 조율, 자동완성 병합, 캐시 등)를 해결하기 위해 분리되었다.
 * 관광지 이름 DB(PlaceQueryRepository)와 인기 검색어 순위(PopularSearchService)를 병합하고,
 * 전용 캐시 저장소(SuggestCacheRepository)를 통해 캐싱 레이어를 다룬다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SuggestService {

    /** 자동완성 최대 반환 건수 (SA 명세 F-SEARCH-003 §4. 최대 5개 반환) */
    public static final int SUGGEST_MAX_SIZE = 5;

    private final PlaceQueryRepository placeQueryRepository;
    private final SuggestCacheRepository suggestCacheRepository;
    private final PopularSearchService popularSearchService;
    private final KakaoLocalApiClient kakaoLocalApiClient;
    private final java.util.concurrent.ExecutorService kakaoVirtualThreadExecutor;

    /**
     * 검색어 자동완성.
     * <p>
     * 1. 캐시 조회 (Hit 시 즉시 반환) <br>
     * 2. 캐시 Miss 시 DB prefix 매칭 조회 <br>
     * 3. 카카오 로컬 API 키워드 검색 보완 (단축 호출) <br>
     * 4. Redis 인기 검색어 ZSet 결과 보완 (단축 호출) <br>
     * 5. 병합(중복 제거) 후 결과 캐싱
     * </p>
     *
     * @param prefix 입력 중인 검색어
     * @return 최대 SUGGEST_MAX_SIZE 개의 자동완성 키워드 (출처 포함)
     */
    public List<SuggestResponse> suggest(String prefix) {
        // 검색어 원문 보안 로깅 (길이만)
        log.info("[SuggestService] 자동완성 요청 - prefixLength: {}", prefix != null ? prefix.length() : 0);

        String normalized = prefix != null ? prefix.strip() : null;

        // 필수 검증
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_SHORT);
        }
        if (normalized.length() > 50) {
            throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_LONG);
        }

        // 1. Redis 캐시 조회
        Optional<List<SuggestResponse>> cached = suggestCacheRepository.get(normalized);
        if (cached.isPresent()) {
            log.debug("[SuggestService] 자동완성 캐시 Hit - prefix: {}", normalized);
            return cached.get();
        }

        Set<SuggestResponse> merged = new LinkedHashSet<>();

        // 2. 캐시 Miss — DB 조회
        List<String> dbResults = placeQueryRepository.suggestByPrefix(normalized, SUGGEST_MAX_SIZE);
        for (String dbResult : dbResults) {
            merged.add(new SuggestResponse(dbResult, SuggestResponse.SuggestSource.DB));
        }

        // 3. 카카오 로컬 API 키워드 검색 보완 (단축 호출)
        boolean kakaoDegraded = false;
        if (merged.size() < SUGGEST_MAX_SIZE) {
            int remainingForKakao = SUGGEST_MAX_SIZE - merged.size();
            KakaoKeywordResponse kakaoResponse = null;
            try {
                kakaoResponse = java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> kakaoLocalApiClient.searchByKeyword(normalized, remainingForKakao),
                        kakaoVirtualThreadExecutor
                ).get(300, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("[SuggestService] Kakao API Suggestion Timeout or Exception: {}", e.getMessage());
                kakaoDegraded = true;
            }
            
            if (kakaoResponse != null && kakaoResponse.documents() != null) {
                for (KakaoKeywordResponse.Document doc : kakaoResponse.documents()) {
                    if (merged.size() >= SUGGEST_MAX_SIZE) break;
                    String placeName = doc.placeName();
                    if (placeName != null && !placeName.isBlank()) {
                        // 중복 체크 로직: 동일 키워드가 DB에 이미 있으면 Kakao 결과를 스킵
                        boolean exists = merged.stream().anyMatch(m -> m.keyword().equals(placeName));
                        if (!exists) {
                            merged.add(new SuggestResponse(placeName, SuggestResponse.SuggestSource.KAKAO));
                        }
                    }
                }
            }
        }

        // 4. 인기 검색어 ZSet 보완 조회 (단축 호출)
        if (merged.size() < SUGGEST_MAX_SIZE) {
            int remainingForRedis = SUGGEST_MAX_SIZE - merged.size();
            List<String> redisResults = popularSearchService.fetchPopularSuggestions(normalized, remainingForRedis);
            for (String redisResult : redisResults) {
                if (merged.size() >= SUGGEST_MAX_SIZE) break;
                boolean exists = merged.stream().anyMatch(m -> m.keyword().equals(redisResult));
                if (!exists) {
                    merged.add(new SuggestResponse(redisResult, SuggestResponse.SuggestSource.REDIS));
                }
            }
        }

        List<SuggestResponse> finalResults = new ArrayList<>(merged);

        // 5. 결과 캐싱 (Kakao 장애 시에는 불완전한 결과를 캐싱하지 않아 다음 요청 시 재시도 유도)
        if (!kakaoDegraded) {
            suggestCacheRepository.set(normalized, finalResults);
        } else {
            log.debug("[SuggestService] Kakao 장애로 인한 불완전한 결과 캐싱 스킵 - prefix: {}", normalized);
        }

        return finalResults;
    }
}
