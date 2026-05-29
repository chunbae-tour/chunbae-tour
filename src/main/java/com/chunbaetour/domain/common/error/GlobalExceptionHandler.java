package com.chunbaetour.domain.common.error;

import com.chunbaetour.domain.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        ErrorCode code = resolveFieldErrorCode(ex);
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        ErrorCode code = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> {
                    String propertyPath = violation.getPropertyPath().toString();
                    if (propertyPath.endsWith("keyword") || propertyPath.endsWith("q")) {
                        if (violation.getConstraintDescriptor().getAnnotation() instanceof jakarta.validation.constraints.Size) {
                            return ErrorCode.SEARCH_KEYWORD_TOO_LONG;
                        }
                    }
                    return ErrorCode.INVALID_REQUEST;
                })
                .orElse(ErrorCode.INVALID_REQUEST);

        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }

    // 낙관적 잠금 충돌 — 동시 요청이 같은 엔티티를 수정한 경우, 클라이언트 재시도로 해결 가능
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
        ErrorCode code = ErrorCode.CONCURRENT_UPDATE;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        throw ex;
    }

    // JSON 역직렬화 실패 (잘못된 enum 값, 타입 불일치 등) — 400 반환
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        log.warn("invalid request body: {}", cause != null ? cause.getClass().getSimpleName() : ex.getClass().getSimpleName());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.getCode(), ErrorCode.INVALID_REQUEST.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequestExceptions(Exception ex) {
        log.warn("Bad Request Exception: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.getCode(), ErrorCode.INVALID_REQUEST.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }

    private ErrorCode resolveFieldErrorCode(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> switch (fieldError.getField()) {
                    case "email" -> ErrorCode.INVALID_EMAIL_FORMAT;
                    case "password" -> ErrorCode.INVALID_PASSWORD_FORMAT;
                    case "originLat", "originLng", "destLat", "destLng" -> ErrorCode.INVALID_LOCATION;
                    case "keyword" -> {
                        String code = fieldError.getCode();
                        if ("NotBlank".equals(code)) yield ErrorCode.SEARCH_KEYWORD_TOO_SHORT;
                        if ("Size".equals(code)) yield ErrorCode.SEARCH_KEYWORD_TOO_LONG;
                        yield ErrorCode.INVALID_REQUEST;
                    }
                    default -> ErrorCode.INVALID_REQUEST;
                })
                .orElse(ErrorCode.INVALID_REQUEST);
    }
}
