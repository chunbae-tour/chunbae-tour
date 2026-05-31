package com.chunbaetour.domain.admin.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * {@link LogAdminAction} 어노테이션 부착 컨트롤러 메서드를 가로채 {@link AdminActionLogService}에 자동
 * 기록을 위임하는 AOP advice (KAN-179, Admin Epic KAN-177 S01).
 *
 * <p>동작 흐름
 * <ol>
 *   <li>컨트롤러 메서드 실행({@code pjp.proceed()}). 메서드가 예외를 던지면 본 advice도 그 예외를 그대로
 *       전파 — record 호출 X. 결과: 액션이 실패한 경우 로그 미발행 (afterCommit과 정합).</li>
 *   <li>실행 성공 시 메서드 시그니처 + Spring Security 컨텍스트 + URI path 변수에서 액션 필드 추출:
 *       <ul>
 *         <li>adminUserId — {@link SecurityContextHolder} principal에서 Long 추출.</li>
 *         <li>actionType / targetType — 어노테이션 속성 그대로.</li>
 *         <li>targetId — URI path 변수에서 첫 번째 Long 값을 추출 (예: {@code /users/{userId}/suspensions} → userId).</li>
 *         <li>reason / beforeStatus / afterStatus — 본 슬라이스는 null (후속 슬라이스가 보강).</li>
 *       </ul>
 *   </li>
 *   <li>{@link AdminActionLogService#record}로 위임 — 이후 afterCommit + REQUIRES_NEW + 실패 흡수가 처리.</li>
 * </ol>
 *
 * <p>본 advice는 path variable에서만 targetId를 추출한다 — admin endpoint 표준이 PathVariable로 대상 id를 받는
 * 패턴이므로(예: {@code /admin/users/{userId}}) 충분. request body 파라미터 등에서 추출은 후속 검토.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AdminActionLogAspect {

    private final AdminActionLogService adminActionLogService;

    @Around("@annotation(logAdminAction)")
    public Object around(ProceedingJoinPoint pjp, LogAdminAction logAdminAction) throws Throwable {
        // 컨트롤러 메서드 실행 — 예외 시 그대로 전파(로그 미발행)
        Object result = pjp.proceed();

        try {
            Long adminUserId = extractAdminUserId();
            Long targetId = extractTargetIdFromPath();
            if (adminUserId == null || targetId == null) {
                // 추출 실패 = 인증/URL 패턴 비표준. 로그 기록을 강제로 시도해 잘못된 데이터를 남기는 것보다
                // 흡수 + 운영자 후속 확인이 안전 (record 호출 자체를 생략).
                log.warn(
                        "AdminActionLog skipped — extraction failed. adminUserId={}, targetId={}, action={}",
                        adminUserId,
                        targetId,
                        logAdminAction.actionType());
                return result;
            }

            AdminActionContext context = new AdminActionContext(
                    adminUserId,
                    logAdminAction.actionType(),
                    logAdminAction.targetType(),
                    targetId,
                    null, // reason 보강은 후속 슬라이스
                    null, // beforeStatus 보강은 후속 슬라이스
                    null); // afterStatus 보강은 후속 슬라이스
            adminActionLogService.record(context);
        } catch (Exception e) {
            // 추출 단계 예외도 흡수 — 본 요청 응답에 영향 X
            log.error("AdminActionLogAspect failed (absorbed) — action={}", logAdminAction.actionType(), e);
        }

        return result;
    }

    /**
     * SecurityContext에서 운영자 userId를 추출한다.
     *
     * <p>본 프로젝트의 인증 표준은 {@code @AuthenticationPrincipal Long userId} 패턴
     * (JwtAuthenticationFilter가 SecurityContext principal에 Long을 채움). 본 메서드는 같은 규약을 가정.
     */
    private Long extractAdminUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long longPrincipal) {
            return longPrincipal;
        }
        return null;
    }

    /**
     * Servlet URI path 변수에서 첫 번째 Long 타입 변수를 추출한다.
     *
     * <p>Spring MVC는 path variable을 request attribute {@code HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE}에
     * Map 형태로 저장한다. 본 메서드는 그 Map을 읽어 Long으로 parse 가능한 첫 값을 반환.
     *
     * <p>가정 — admin endpoint는 URL에 대상 id 1개를 노출하는 표준 패턴
     * (예: {@code /admin/users/{userId}}, {@code /admin/shop-certifications/{applicationId}}).
     * 여러 path variable이 있으면 첫 Long을 사용 — 호출자가 URL 설계 시 대상 id를 첫 변수로 두는 관례 유지 필요.
     */
    @SuppressWarnings("unchecked")
    private Long extractTargetIdFromPath() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        Object pathVarsAttr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(pathVarsAttr instanceof Map<?, ?> pathVars)) {
            return null;
        }
        for (Object value : pathVars.values()) {
            if (value == null) {
                continue;
            }
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
                // 다음 path variable 후보로 진행
            }
        }
        return null;
    }
}
