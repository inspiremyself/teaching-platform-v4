package com.opencode.teachingplatform.common.exception;

import com.opencode.teachingplatform.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (ex.getCode() >= 40300 && ex.getCode() < 40400) {
            status = HttpStatus.FORBIDDEN;
        } else if (ex.getCode() >= 40400 && ex.getCode() < 40500) {
            status = HttpStatus.NOT_FOUND;
        } else if (ex.getCode() >= 40900 && ex.getCode() < 41000) {
            status = HttpStatus.CONFLICT;
        }
        return ResponseEntity.status(status).body(ApiResponse.of(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.of(40000, message, null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.of(40001, ex.getMessage(), null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        BusinessException businessCause = findBusinessCause(ex);
        if (businessCause != null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.of(businessCause.getCode(), businessCause.getMessage(), null));
        }
        log.error("Request body unreadable", ex);
        return ResponseEntity.badRequest().body(ApiResponse.of(40000, "请求体 JSON 格式不正确", null));
    }

    /**
     * 反序列化阶段抛出的业务异常会被 Jackson 与 Spring 各包一层，透出原始 code 与文案，
     * 避免把「字段不支持」误报成笼统的 JSON 格式错误。
     */
    private BusinessException findBusinessCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BusinessException businessException) {
                return businessException;
            }
            current = current.getCause();
        }
        return null;
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.of(40300, "Access Denied", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception ex) {
        // 全局兜底脱敏：响应不携带异常信息，完整堆栈只写日志，避免泄露 SQL / 表名 / 类名
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.of(50000, "系统繁忙，请稍后重试", null));
    }
}
