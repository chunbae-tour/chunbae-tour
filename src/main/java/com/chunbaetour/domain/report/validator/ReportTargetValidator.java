package com.chunbaetour.domain.report.validator;

import com.chunbaetour.domain.report.entity.ReportTargetType;

/**
 * 신고 대상 검증 전략 인터페이스 (OCP).
 *
 * <p>구현체는 {@link ReportTargetType} 하나를 담당하며 대상 존재·활성 상태·자기신고 여부를 검증한다.
 * 새 도메인 추가 시 이 인터페이스를 구현하는 {@code @Component}만 추가하면 된다.
 *
 * @return {@code validate()} 피신고 콘텐츠 작성자 userId (제재 누적 집계용)
 */
public interface ReportTargetValidator {

    ReportTargetType supportType();

    Long validate(Long targetId, Long reporterId);
}
