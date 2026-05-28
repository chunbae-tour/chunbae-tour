package com.chunbaetour.domain.common.entity;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// ERD: common_error_logs — Translation / AI FAQ 외부 API 실패 로그 공통 Entity, 수정 없는 append-only
@Entity
@Table(name = "common_error_logs", indexes = @Index(name = "idx_common_error_logs_domain_created", columnList = "domain, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CommonErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 도메인에서 발생한 에러인지 (예: TRANSLATION, AI_FAQ)
    @Column(nullable = false, length = 50)
    private String domain;

    // 에러 유형 (예: API_CALL_FAILURE, RATE_LIMIT_EXCEEDED, TIMEOUT)
    @Column(name = "error_type", nullable = false, length = 50)
    private String errorType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    // 상세 정보 — 응답 바디, 스택 트레이스 등 선택적
    @Column(columnDefinition = "TEXT")
    private String detail;

    // 외부 서비스명 (예: GOOGLE_TRANSLATION, OPENAI)
    @Column(name = "external_provider", length = 50)
    private String externalProvider;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private CommonErrorLog(String domain, String errorType, String message,
                           String detail, String externalProvider) {
        if (domain == null || domain.isBlank()) throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        if (errorType == null || errorType.isBlank()) throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        if (message == null || message.isBlank()) throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        this.domain = domain;
        this.errorType = errorType;
        this.message = message;
        this.detail = detail;
        this.externalProvider = externalProvider;
    }
}
