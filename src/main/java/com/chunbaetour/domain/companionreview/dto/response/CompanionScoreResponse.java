package com.chunbaetour.domain.companionreview.dto.response;

import com.chunbaetour.domain.auth.Account;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record CompanionScoreResponse(
        Long userId,
        String nickname,
        float averageScore,
        int reviewCount,
        Map<Integer, Long> scoreDistribution
) {
    // scoreDistribution: {1: count, 2: count, ..., 5: count} — 리뷰 없는 점수는 0
    public static CompanionScoreResponse of(Account account, List<Object[]> rawDistribution) {
        Map<Integer, Long> distribution = Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L);
        Map<Integer, Long> counted = rawDistribution.stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).intValue(),
                        row -> ((Number) row[1]).longValue()
                ));
        Map<Integer, Long> merged = new java.util.HashMap<>(distribution);
        merged.putAll(counted);

        return new CompanionScoreResponse(
                account.getId(),
                account.getNickname(),
                account.getCompanionScore(),
                account.getCompanionReviewCount(),
                merged
        );
    }
}
