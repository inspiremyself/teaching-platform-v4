package com.opencode.teachingplatform.common.exception;

import com.opencode.teachingplatform.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link GlobalExceptionHandler} 全局兜底脱敏单元测试。
 */
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unhandledExceptionReturnsRedactedMessage() {
        RuntimeException ex = new RuntimeException("SQL error in table secret_table: password_hash");

        ResponseEntity<ApiResponse<Object>> response = handler.handleOther(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiResponse<Object> body = response.getBody();
        assertEquals(50000, body.code());
        assertEquals("系统繁忙，请稍后重试", body.message());
        assertNull(body.data());
        assertFalse(body.message().contains("secret_table"));
        assertFalse(body.message().contains("password_hash"));
    }
}
