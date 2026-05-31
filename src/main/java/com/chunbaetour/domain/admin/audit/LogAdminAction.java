package com.chunbaetour.domain.admin.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 운영 액션 자동 기록 메서드 어노테이션 (KAN-179, Admin Epic KAN-177 S01).
 *
 * <p>admin 컨트롤러 메서드에 부착하면 {@link AdminActionLogAspect}가 메서드 호출 후 자동으로
 * {@link AdminActionLogService#record}를 호출해 액션 1건을 기록한다.
 *
 * <p>설계 원칙
 * <ul>
 *   <li><b>호출자 코드 변경 0</b> — 어노테이션만 부착하면 끝. 본 슬라이스에서는 어노테이션과 advice만 제공하고,
 *       후속 슬라이스(S02 등)가 각자 컨트롤러에 부착하는 wiring을 한다.</li>
 *   <li><b>RUNTIME 보유</b> — AOP advice가 런타임에 어노테이션 메타데이터를 읽어 actionType/targetType을 추출.</li>
 *   <li><b>METHOD 한정</b> — 컨트롤러 메서드 단위로만 적용. 클래스 단위 미지원 (액션 종류는 endpoint별로 다름).</li>
 *   <li><b>쓰기 트랜잭션 메서드에만 부착</b> — afterCommit 발화 기반이므로 {@code @Transactional(readOnly=true)}
 *       메서드에 부착하면 의도외 audit이 생긴다. readOnly 트랜잭션에서는 로그를 미발행한다
 *       ({@link AdminActionLogService#record} 가드). 조회성 메서드에는 부착 금지.</li>
 * </ul>
 *
 * <p>필수 속성 두 개({@link #actionType()} / {@link #targetType()})만 받는다. {@code targetId}는 URI path
 * 변수에서 advice가 추출한다 — {@link #targetIdVar()}로 추출 대상 변수를 명시하면 결정적, 미지정 시 휴리스틱.
 *
 * <p>{@code reason} / {@code beforeStatus} / {@code afterStatus}는 본 슬라이스에서 항상 null이다. 후속
 * 슬라이스가 request body/result에서 추출해 보강하되, 그 보강은 별도 전략(어노테이션 속성이 아님)이다 —
 * 본 어노테이션에 {@code reason()} 등의 속성을 추가하지 않는다(YAGNI).
 *
 * <p>사용 예
 * <pre>
 * &#64;PostMapping("/api/v1/admin/users/{userId}/suspensions")
 * &#64;LogAdminAction(actionType = AdminActionType.USER_SUSPEND, targetType = AdminTargetType.USER,
 *                 targetIdVar = "userId")
 * public ApiResponse&lt;...&gt; suspend(&#64;PathVariable Long userId, ...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogAdminAction {

    AdminActionType actionType();

    AdminTargetType targetType();

    /**
     * targetId를 추출할 path variable 이름. 지정 시 해당 변수를 결정적으로 조회한다.
     *
     * <p>미지정("")이면 URI path 변수 중 Long으로 parse 가능한 후보를 탐색하되, 후보가 2개 이상이면 대상 식별이
     * 모호하므로 기록을 생략한다(targetId=null → record skip). path variable Map 순회 순서는 비보장이므로
     * 변수가 여러 개인 endpoint(예: {@code /shops/{shopId}/products/{productId}})는 본 속성으로 명시할 것.
     *
     * <p>예: {@code @LogAdminAction(..., targetIdVar = "userId")}
     */
    String targetIdVar() default "";
}
