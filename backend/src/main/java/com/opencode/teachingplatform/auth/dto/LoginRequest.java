package com.opencode.teachingplatform.auth.dto;

import com.opencode.teachingplatform.auth.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        @NotNull(message = "角色不能为空") UserRole role,
        boolean forceLogin,
        String schoolCode
) {
    public LoginRequest(String username, String password, UserRole role) {
        this(username, password, role, false, null);
    }

    public LoginRequest(String username, String password, UserRole role, boolean forceLogin) {
        this(username, password, role, forceLogin, null);
    }
}
