package com.chunbaetour.domain.admin.audit;

import com.chunbaetour.domain.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
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
 *         <li>targetId — URI path 변수에서 추출. {@code @LogAdminAction.targetIdVar} 지정 시 해당 변수를
 *             결정적으로 조회, 미지정 시 Long 후보가 정확히 1개일 때만 채택(2개 이상은 모호 → 기록 생략).</li>
 *         <li>reason / beforeStatus / afterStatus — 본 슬라이스는 null (후속 슬라이스가 보강).</li>
 *       </ul>
 *   </li>
 *   <li>{@link AdminActionLogService#record}로 위임 — 이후 afterCommit + REQUIRES_NEW + 실패 흡수가 처리.</li>
 * </ol>
 *
 * <p>본 advice는 path variable에서만 targetId를 추출한다 — admin endpoint 표준이 PathVariable로 대상 id를 받는
 * 패턴이므로(예: {@code /admin/users/{userId}}) 충분. 다중 path 변수 endpoint는 {@code targetIdVar}로 대상을
 * 명시. request body 파라미터 등에서 추출은 후속 검토.
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
            Long targetId = extractTargetIdFromPath(logAdminAction.targetIdVar());
            // path에서 못 구했고 returnIdField 지정 시(생성 endpoint 등) 반환값에서 추출. 하위호환:
            // returnIdField 미지정("")이면 이 경로 진입 안 함 → 기존 동작 불변.
            if (targetId == null && !logAdminAction.returnIdField().isBlank()) {
                targetId = extractTargetIdFromReturn(result, logAdminAction.returnIdField());
            }
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
     * Servlet URI path 변수에서 targetId를 추출한다.
     *
     * <p>Spring MVC는 path variable을 request attribute {@code HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE}에
     * Map 형태로 저장한다. 본 메서드는 그 Map을 읽어 추출한다.
     *
     * <ul>
     *   <li><b>{@code targetIdVar} 지정 시</b> — 해당 이름의 path 변수를 정확히 조회 → Long parse. 없거나
     *       parse 실패 시 null. URL 컨벤션/Map 순회 순서에 의존하지 않는 결정적 추출.</li>
     *   <li><b>{@code targetIdVar} 미지정("") 시</b> — Long으로 parse 가능한 후보를 탐색한다. 후보가 정확히
     *       1개면 그 값, <b>2개 이상이면 대상 식별이 모호하므로 null</b>(기록 생략). path variable Map의 순회
     *       순서는 비보장이므로 "첫 Long" 휴리스틱은 다중 변수 endpoint에서 깨질 수 있어 채택하지 않는다.</li>
     * </ul>
     *
     * <p>변수가 여러 개인 endpoint(예: {@code /shops/{shopId}/products/{productId}})는 {@code targetIdVar}로
     * 명시할 것 — 미지정 시 모호 판정으로 targetId=null이 되어 record가 skip된다.
     */
    private Long extractTargetIdFromPath(String targetIdVar) {
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

        if (!targetIdVar.isBlank()) {
            // 결정적 경로 — 지정된 변수만 조회
            return parseLongOrNull(pathVars.get(targetIdVar));
        }

        // 휴리스틱 경로 — Long 후보 정확히 1개일 때만 채택, 2개 이상은 모호 → null
        Long candidate = null;
        for (Object value : pathVars.values()) {
            Long parsed = parseLongOrNull(value);
            if (parsed == null) {
                continue;
            }
            if (candidate != null) {
                // Long path variable 2개 이상 → 대상 식별 모호 → 기록 생략
                return null;
            }
            candidate = parsed;
        }
        return candidate;
    }

    /**
     * 메서드 반환값에서 targetId를 추출한다(생성 endpoint 등 path에 id가 없는 경우).
     *
     * <p>컨트롤러 표준 반환은 {@code ApiResponse<T>} 래퍼이므로 {@code data()}로 본문 DTO를 꺼낸 뒤, DTO가
     * record라는 전제로 {@code fieldName}과 동일 이름의 <b>접근자 메서드</b>(예: {@code id()})를 리플렉션 호출한다.
     * bean getter 규칙({@code getId()})이 아니라 record accessor 이름과 정확히 일치해야 한다.
     *
     * <p>접근자 부재/타입 불일치/null 등 추출 실패는 {@code null}로 흡수 — record는 skip되고 본 요청에는 영향 없음.
     */
    private Long extractTargetIdFromReturn(Object result, String fieldName) {
        Object body = (result instanceof ApiResponse<?> response) ? response.data() : result;
        if (body == null) {
            return null;
        }
        try {
            Method accessor = body.getClass().getMethod(fieldName);
            return parseLongOrNull(accessor.invoke(body));
        } catch (Exception e) {
            log.warn(
                    "AdminActionLog return id extraction failed — field={}, type={}",
                    fieldName,
                    body.getClass().getSimpleName());
            return null;
        }
    }

    /** path 변수 값을 Long으로 parse. null/비Long 시 null. */
    private Long parseLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
