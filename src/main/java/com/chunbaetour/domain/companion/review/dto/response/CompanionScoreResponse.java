package com.chunbaetour.domain.companion.review.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.companion.review.repository.ScoreCountProjection;
import java.util.List;
import java.util.Map;

public record CompanionScoreResponse(
        Long userId,
        String nickname,
        double averageScore,
        int reviewCount,
        Map<String, Long> scoreDistribution
) {
    // scoreDistribution: {"1": count, ..., "5": count} — JSON 키는 String, 리뷰 없는 점수는 0
    public static CompanionScoreResponse of(Account account, List<ScoreCountProjection> distribution) {
        Map<String, Long> dist = new java.util.HashMap<>(
                Map.of("1", 0L, "2", 0L, "3", 0L, "4", 0L, "5", 0L));
        distribution.forEach(row -> {
            Integer score = row.getScore();
            Long count = row.getCount();
            if (score != null && score >= 1 && score <= 5) {
                dist.put(String.valueOf(score), count == null ? 0L : count);
            }
        });

        return new CompanionScoreResponse(
                account.getId(),
                account.getNickname(),
                account.getCompanionScore(),
                account.getCompanionReviewCount(),
                dist
        );
    }
}
