package com.opencode.teachingplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank(message = "旧密码不能为空") String oldPassword,
        @NotBlank(message = "新密码不能为空")
        // 长度与字符组成合并为单条规则，避免 @Size 与 @Pattern 同时命中时文案重复拼接
        @Pattern(regexp = "^(?:(?=.*[A-Za-z])(?=.*\\d).{8,64})?$",
                 message = "新密码需 8-64 位，且同时包含字母和数字")
        String newPassword
) {
}
