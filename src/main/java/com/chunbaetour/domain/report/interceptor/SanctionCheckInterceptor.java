package com.chunbaetour.domain.report.interceptor;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 신고 제재 차단 인터셉터.
 *
 * <p>POST/PATCH/DELETE 요청에서:
 * <ol>
 *   <li>Account.status == SUSPENDED 이고 제재가 만료되지 않은 경우 → ACCOUNT_SUSPENDED</li>
 *   <li>URL → 도메인 매핑 후 해당 도메인에 활성 user_sanction 존재 시 → DOMAIN_SANCTIONED</li>
 * </ol>
 *
 * <p>GET 요청, 비인증 요청, 관리자(/admin/**) 경로는 스킵.
 */
@Component
@RequiredArgsConstructor
public class SanctionCheckInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 쓰기 URL → 도메인별 제재 targetType 매핑.
     * LinkedHashMap: 삽입 순서 유지 — 구체적인 패턴을 와일드카드보다 먼저 매칭되도록 필요 시 정렬 가능.
     */
    private static final Map<String, ReportTargetType> DOMAIN_PATTERNS;

    static {
        DOMAIN_PATTERNS = new LinkedHashMap<>();
        DOMAIN_PATTERNS.put("/api/v1/community/posts/companions/**", ReportTargetType.POST_COMPANION);
        DOMAIN_PATTERNS.put("/api/v1/community/posts/free/**",       ReportTargetType.POST_FREE);
        DOMAIN_PATTERNS.put("/api/v1/community/comments/**",         ReportTargetType.COMMENT);
        DOMAIN_PATTERNS.put("/api/v1/places/*/reviews/**",          ReportTargetType.REVIEW);
    }

    private final AccountRepository accountRepository;
    private final UserSanctionRepository userSanctionRepository;
    private final Clock clock;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // GET은 제재와 무관 (읽기 전용)
        if ("GET".equals(request.getMethod())) return true;

        // 비인증 요청 — Spring Security가 적절히 처리
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) return true;

        String path = request.getServletPath();

        // 관리자 경로는 제재 적용 제외
        if (PATH_MATCHER.match("/api/v1/admin/**", path)) return true;

        LocalDateTime now = LocalDateTime.now(clock);

        // 1. Account 레벨 정지 체크 (JWT 발급 후 정지된 경우 대비)
        Account account = accountRepository.findById(userId).orElse(null);
        if (account != null && account.getStatus() == AccountStatus.SUSPENDED
                && account.isCurrentlySanctioned(now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        // 2. 도메인별 제재 체크
        ReportTargetType domain = resolveDomain(path);
        if (domain != null) {
            boolean hasDomainSanction = !userSanctionRepository
                    .findActiveSanctionsByUserIdAndTargetType(userId, domain, now)
                    .isEmpty();
            if (hasDomainSanction) {
                throw new BusinessException(ErrorCode.DOMAIN_SANCTIONED);
            }
        }

        return true;
    }

    private ReportTargetType resolveDomain(String path) {
        for (Map.Entry<String, ReportTargetType> entry : DOMAIN_PATTERNS.entrySet()) {
            if (PATH_MATCHER.match(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
