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
 * </ul>
 *
 * <p>필수 속성 두 개({@link #actionType()} / {@link #targetType()})만 받는다. {@code targetId} /
 * {@code beforeStatus} / {@code afterStatus}는 메서드 시그니처와 리턴값에서 advice가 추출한다.
 *
 * <p>사용 예
 * <pre>
 * &#64;PostMapping("/api/v1/admin/users/{userId}/suspensions")
 * &#64;LogAdminAction(actionType = AdminActionType.USER_SUSPEND, targetType = AdminTargetType.USER)
 * public ApiResponse&lt;...&gt; suspend(&#64;PathVariable Long userId, ...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogAdminAction {

    AdminActionType actionType();

    AdminTargetType targetType();
}
